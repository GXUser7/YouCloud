-- «Любимое», the same on every device signed in to the account: the tracks liked in the app —
-- which the app keeps downloaded — and on the site. One row a track of a service. A track taken
-- out stays as a row with liked = false, so a device that still has it lets it go too. Files from
-- the phone itself are nowhere else to be found and are never sent.
--
-- Devices write only through save_favorites, which keeps the later of two changes to a track, and
-- read what changed since they last looked (updated_at, the server's own time) through
-- favorites_since. The site and an open app hear of each change as it is made (Realtime).

create table public.favorites (
    user_id uuid not null references public.profiles on delete cascade,
    service text not null check (service in ('soundcloud', 'yandex', 'youtube')),
    -- The track's own id there: a number for SoundCloud and Yandex, the video's for YouTube.
    track_id text not null check (track_id ~ '^[A-Za-z0-9_-]{1,64}$'),
    liked boolean not null default true,
    -- When the listener liked it or took it out, by the clock of the device that did it: of two
    -- changes the later stays.
    changed_at timestamptz not null,
    -- When the server took the change: what a device asks for since its last look.
    updated_at timestamptz not null default now(),
    -- The track as a list shows it, for a device that hasn't met it yet.
    title text not null default '' check (char_length(title) <= 500),
    -- [{"id": the artist's id on the service, "name", "link"?}]
    artists jsonb not null default '[]' check (jsonb_typeof(artists) = 'array' and octet_length(artists::text) <= 4096),
    album_id text check (char_length(album_id) <= 64),
    cover text check (char_length(cover) <= 1024),
    duration_ms bigint check (duration_ms >= 0),
    link text check (char_length(link) <= 1024),
    -- A YouTube broadcast: it plays while it is on, and there is nothing to download.
    live boolean not null default false,
    primary key (user_id, service, track_id)
);

create index favorites_changes on public.favorites (user_id, updated_at);

alter table public.favorites enable row level security;

create policy "own favorites are seen" on public.favorites
    for select to authenticated using (user_id = (select auth.uid()));

revoke all on public.favorites from anon;
revoke insert, update, delete on public.favorites from authenticated;

-- Writes [items], a JSON array of rows as above without user_id and updated_at, as the caller's:
-- each over the row already there, unless that one holds a later change. A track taken out may
-- come without its title and the rest; the row keeps what it had.
create function public.save_favorites(items jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare
    me uuid := auth.uid();
begin
    if me is null then raise exception 'not signed in' using errcode = '28000'; end if;
    if jsonb_typeof(items) <> 'array' or jsonb_array_length(items) > 500 then
        raise exception 'an array of at most 500' using errcode = '22023';
    end if;

    insert into public.favorites as f
        (user_id, service, track_id, liked, changed_at, updated_at, title, artists, album_id, cover, duration_ms, link, live)
    select distinct on (i.service, i.track_id)
        me, i.service, i.track_id, coalesce(i.liked, true),
        -- A device whose clock runs ahead can't put its change past every later one.
        least(coalesce(i.changed_at, now()), now()),
        now(),
        coalesce(i.title, ''), coalesce(i.artists, '[]'::jsonb), i.album_id, i.cover, i.duration_ms, i.link,
        coalesce(i.live, false)
    from jsonb_to_recordset(items) as i(
        service text, track_id text, liked boolean, changed_at timestamptz, title text, artists jsonb,
        album_id text, cover text, duration_ms bigint, link text, live boolean
    )
    order by i.service, i.track_id, i.changed_at desc nulls last
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

-- What changed in the caller's «Любимое» after [since] — or, with none (a device's first look),
-- every track in it — and the time to ask from next.
create function public.favorites_since(since timestamptz default null) returns jsonb
language sql stable security definer set search_path = '' as $$
    select jsonb_build_object(
        'until', coalesce((select max(x.updated_at) from public.favorites x where x.user_id = auth.uid()), now()),
        'items', coalesce(
            (select jsonb_agg(to_jsonb(f) - 'user_id' - 'updated_at' order by f.changed_at desc)
             from public.favorites f
             where f.user_id = auth.uid()
               and case when since is null then f.liked else f.updated_at > since end),
            '[]'::jsonb
        )
    )
    where auth.uid() is not null;
$$;

revoke execute on function public.save_favorites(jsonb), public.favorites_since(timestamptz) from public, anon;
grant execute on function public.save_favorites(jsonb), public.favorites_since(timestamptz) to authenticated;

-- Each device hears of the account's own changes only: the row rule above decides.
alter publication supabase_realtime add table public.favorites;
