-- A device whose clock runs ahead of the server's sent times past now, and save_favorites held
-- each one to now: a phone's whole «Любимое», sent at once on its first look a second and a
-- half ahead, came out liked all at the same moment, in no order. Now a batch is moved back as
-- one, by as much as its latest change is ahead, so the order within it stays.

create or replace function public.save_favorites(items jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare
    me uuid := auth.uid();
begin
    if me is null then raise exception 'not signed in' using errcode = '28000'; end if;
    if jsonb_typeof(items) <> 'array' or jsonb_array_length(items) > 500 then
        raise exception 'an array of at most 500' using errcode = '22023';
    end if;

    with given as (
        select * from jsonb_to_recordset(items) as i(
            service text, track_id text, liked boolean, changed_at timestamptz, title text, artists jsonb,
            album_id text, cover text, duration_ms bigint, link text, live boolean
        )
    ), ahead as (
        -- How far the device's clock is past the server's, as far as this batch shows.
        select greatest(max(g.changed_at) - now(), interval '0') as by from given g
    )
    insert into public.favorites as f
        (user_id, service, track_id, liked, changed_at, updated_at, title, artists, album_id, cover, duration_ms, link, live)
    select distinct on (g.service, g.track_id)
        me, g.service, g.track_id, coalesce(g.liked, true),
        coalesce(g.changed_at - a.by, now()),
        now(),
        coalesce(g.title, ''), coalesce(g.artists, '[]'::jsonb), g.album_id, g.cover, g.duration_ms, g.link,
        coalesce(g.live, false)
    from given g cross join ahead a
    order by g.service, g.track_id, g.changed_at desc nulls last
    on conflict (user_id, service, track_id) do update set
        liked = excluded.liked,
        changed_at = excluded.changed_at,
        updated_at = excluded.updated_at,
        title = coalesce(nullif(excluded.title, ''), f.title),
        artists = case when excluded.artists = '[]'::jsonb then f.artists else excluded.artists end,
        album_id = coalesce(excluded.album_id, f.album_id),
        cover = coalesce(excluded.cover, f.cover),
        duration_ms = coalesce(excluded.duration_ms, f.duration_ms),
        link = coalesce(excluded.link, f.link),
        live = case when excluded.liked then excluded.live else f.live end
        where excluded.changed_at >= f.changed_at;

    -- Room for any real library, not for filling the database.
    if (select count(*) from public.favorites where user_id = me) > 50000 then
        raise exception 'too many favorites' using errcode = '54000';
    end if;
end;
$$;

revoke execute on function public.save_favorites(jsonb) from public, anon;
grant execute on function public.save_favorites(jsonb) to authenticated;
