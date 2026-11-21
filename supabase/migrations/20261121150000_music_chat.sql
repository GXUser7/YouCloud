-- Music chat between friends for YouCloud.
-- Friends share playable tracks and albums with reactions (emoji) and 1-5 ratings.
-- Free-form text messages do not exist by design.

-- region Tables

create table public.chat_messages (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null references public.profiles on delete cascade,
    recipient_id uuid not null references public.profiles on delete cascade,
    kind text not null check (kind in ('track', 'album')),
    service text not null check (service in ('soundcloud', 'yandex', 'youtube', 'spotify')),
    item_url text not null check (char_length(item_url) between 8 and 1024 and item_url ~* '^https?://'),
    title text not null check (char_length(btrim(title)) between 1 and 500),
    artist text not null default '' check (char_length(artist) <= 500),
    cover_url text check (cover_url is null or char_length(cover_url) <= 1024),
    duration_ms bigint check (duration_ms is null or duration_ms >= 0),
    track_count integer check (track_count is null or track_count > 0),
    read_at timestamptz,
    created_at timestamptz not null default now(),
    check (sender_id <> recipient_id)
);

create table public.chat_reactions (
    message_id uuid not null references public.chat_messages on delete cascade,
    user_id uuid not null references public.profiles on delete cascade,
    emoji text check (emoji is null or char_length(btrim(emoji)) between 1 and 16),
    rating smallint check (rating is null or rating between 1 and 5),
    updated_at timestamptz not null default now(),
    primary key (message_id, user_id),
    check (emoji is not null or rating is not null)
);

create index chat_messages_pair_created
    on public.chat_messages (least(sender_id, recipient_id), greatest(sender_id, recipient_id), created_at desc, id desc);

create index chat_messages_unread_recipient
    on public.chat_messages (recipient_id, created_at desc)
    where read_at is null;

create index chat_messages_sender_created
    on public.chat_messages (sender_id, created_at desc);

create index chat_messages_recipient_created
    on public.chat_messages (recipient_id, created_at desc);

create index chat_reactions_user_updated
    on public.chat_reactions (user_id, updated_at desc);

-- endregion

-- region Row level security

alter table public.chat_messages enable row level security;
alter table public.chat_reactions enable row level security;

create policy "own chat messages are seen" on public.chat_messages
    for select to authenticated
    using ((select auth.uid()) in (sender_id, recipient_id));

create policy "reactions on own chats are seen" on public.chat_reactions
    for select to authenticated
    using (
        exists (
            select 1 from public.chat_messages m
            where m.id = message_id
              and (select auth.uid()) in (m.sender_id, m.recipient_id)
        )
    );

revoke all on public.chat_messages, public.chat_reactions from anon;
grant select on public.chat_messages, public.chat_reactions to authenticated;
revoke insert, update, delete on public.chat_messages, public.chat_reactions from authenticated;

-- endregion

-- region RPC functions

create function public.send_chat_item(
    recipients uuid[],
    p_kind text,
    p_service text,
    p_item_url text,
    p_title text,
    p_artist text default '',
    p_cover_url text default null,
    p_duration_ms bigint default null,
    p_track_count integer default null
) returns integer
language plpgsql security definer set search_path = '' as $$
declare
    me uuid := auth.uid();
    target uuid;
    inserted_count integer := 0;
begin
    if me is null then
        raise exception 'not signed in' using errcode = '28000';
    end if;
    if recipients is null or coalesce(array_length(recipients, 1), 0) = 0 then
        raise exception 'no recipients' using errcode = '22023';
    end if;
    if array_length(recipients, 1) > 25 then
        raise exception 'too many recipients' using errcode = '22023';
    end if;

    for target in select distinct r from unnest(recipients) as r where r is not null and r <> me loop
        if not public.are_friends(me, target) then
            raise exception 'not friends' using errcode = '42501';
        end if;

        insert into public.chat_messages (
            sender_id, recipient_id, kind, service, item_url, title, artist, cover_url, duration_ms, track_count
        ) values (
            me,
            target,
            p_kind,
            p_service,
            btrim(p_item_url),
            left(btrim(p_title), 500),
            left(coalesce(btrim(p_artist), ''), 500),
            nullif(btrim(p_cover_url), ''),
            case when p_kind = 'track' then p_duration_ms else null end,
            case when p_kind = 'album' then p_track_count else null end
        );
        inserted_count := inserted_count + 1;
    end loop;

    return inserted_count;
end;
$$;

create function public.chat_inbox() returns jsonb
language sql stable security definer set search_path = '' as $$
    with me as (
        select auth.uid() as uid
    ),
    accepted_friends as (
        select
            p.id,
            p.nick,
            p.name,
            p.color,
            p.avatar_v
        from me
        join public.friendships f
          on f.status = 'accepted'
         and me.uid in (f.requester, f.addressee)
        join public.profiles p
          on p.id = case when f.requester = me.uid then f.addressee else f.requester end
    ),
    unread_counts as (
        select m.sender_id as peer_id, count(*)::integer as unread_count
        from me
        join public.chat_messages m
          on m.recipient_id = me.uid
         and m.read_at is null
        group by m.sender_id
    ),
    latest_per_friend as (
        select distinct on (af.id)
            af.id as peer_id,
            m.id as message_id,
            m.sender_id,
            m.kind,
            m.service,
            m.item_url,
            m.title,
            m.artist,
            m.cover_url,
            m.duration_ms,
            m.track_count,
            m.created_at,
            (
                select coalesce(jsonb_agg(jsonb_build_object(
                    'user_id', r.user_id,
                    'emoji', r.emoji,
                    'rating', r.rating,
                    'updated_at', r.updated_at
                ) order by r.updated_at desc), '[]'::jsonb)
                from public.chat_reactions r
                where r.message_id = m.id
            ) as reactions
        from me
        join accepted_friends af on true
        left join public.chat_messages m
          on least(m.sender_id, m.recipient_id) = least(me.uid, af.id)
         and greatest(m.sender_id, m.recipient_id) = greatest(me.uid, af.id)
        order by af.id, m.created_at desc nulls last, m.id desc nulls last
    ),
    recent_incoming as (
        select
            m.id,
            m.sender_id,
            m.recipient_id,
            m.kind,
            m.service,
            m.item_url,
            m.title,
            m.artist,
            m.cover_url,
            m.duration_ms,
            m.track_count,
            m.read_at,
            m.created_at,
            p.nick as sender_nick,
            p.name as sender_name,
            p.color as sender_color,
            p.avatar_v as sender_avatar_v
        from me
        join public.chat_messages m on m.recipient_id = me.uid
        join accepted_friends af on af.id = m.sender_id
        join public.profiles p on p.id = m.sender_id
        order by (m.read_at is null) desc, m.created_at desc
        limit 15
    )
    select jsonb_build_object(
        'unread_total', coalesce((select sum(u.unread_count)::integer from unread_counts u), 0),
        'new_items', coalesce((
            select jsonb_agg(jsonb_build_object(
                'id', ri.id,
                'sender_id', ri.sender_id,
                'recipient_id', ri.recipient_id,
                'kind', ri.kind,
                'service', ri.service,
                'item_url', ri.item_url,
                'title', ri.title,
                'artist', ri.artist,
                'cover_url', ri.cover_url,
                'duration_ms', ri.duration_ms,
                'track_count', ri.track_count,
                'read_at', ri.read_at,
                'created_at', ri.created_at,
                'sender_nick', ri.sender_nick,
                'sender_name', ri.sender_name,
                'sender_color', ri.sender_color,
                'sender_avatar_v', ri.sender_avatar_v
            ) order by (ri.read_at is null) desc, ri.created_at desc)
            from recent_incoming ri
        ), '[]'::jsonb),
        'threads', coalesce((
            select jsonb_agg(jsonb_build_object(
                'peer_id', af.id,
                'nick', af.nick,
                'name', af.name,
                'color', af.color,
                'avatar_v', af.avatar_v,
                'unread_count', coalesce(uc.unread_count, 0),
                'last_message', case
                    when lpf.message_id is null then null
                    else jsonb_build_object(
                        'id', lpf.message_id,
                        'sender_id', lpf.sender_id,
                        'kind', lpf.kind,
                        'service', lpf.service,
                        'item_url', lpf.item_url,
                        'title', lpf.title,
                        'artist', lpf.artist,
                        'cover_url', lpf.cover_url,
                        'duration_ms', lpf.duration_ms,
                        'track_count', lpf.track_count,
                        'created_at', lpf.created_at,
                        'reactions', lpf.reactions
                    )
                end
            ) order by lpf.created_at desc nulls last, coalesce(nullif(af.name, ''), af.nick) asc)
            from accepted_friends af
            left join unread_counts uc on uc.peer_id = af.id
            left join latest_per_friend lpf on lpf.peer_id = af.id
        ), '[]'::jsonb)
    )
    where exists (select 1 from me where uid is not null);
$$;

create function public.chat_messages_with(peer uuid, p_limit integer default 80) returns jsonb
language sql stable security definer set search_path = '' as $$
    with me as (
        select auth.uid() as uid
    ),
    msgs as (
        select
            m.id,
            m.sender_id,
            m.recipient_id,
            m.kind,
            m.service,
            m.item_url,
            m.title,
            m.artist,
            m.cover_url,
            m.duration_ms,
            m.track_count,
            m.read_at,
            m.created_at
        from me
        join public.chat_messages m
          on least(m.sender_id, m.recipient_id) = least(me.uid, peer)
         and greatest(m.sender_id, m.recipient_id) = greatest(me.uid, peer)
        where me.uid is not null
          and peer is not null
          and peer <> me.uid
          and public.are_friends(me.uid, peer)
        order by m.created_at desc, m.id desc
        limit greatest(1, least(coalesce(p_limit, 80), 200))
    )
    select coalesce(
        jsonb_agg(jsonb_build_object(
            'id', m.id,
            'sender_id', m.sender_id,
            'recipient_id', m.recipient_id,
            'kind', m.kind,
            'service', m.service,
            'item_url', m.item_url,
            'title', m.title,
            'artist', m.artist,
            'cover_url', m.cover_url,
            'duration_ms', m.duration_ms,
            'track_count', m.track_count,
            'read_at', m.read_at,
            'created_at', m.created_at,
            'reactions', coalesce((
                select jsonb_agg(jsonb_build_object(
                    'user_id', r.user_id,
                    'emoji', r.emoji,
                    'rating', r.rating,
                    'updated_at', r.updated_at
                ) order by r.updated_at desc)
                from public.chat_reactions r
                where r.message_id = m.id
            ), '[]'::jsonb)
        ) order by m.created_at asc, m.id asc),
        '[]'::jsonb
    )
    from msgs m;
$$;

create function public.mark_chat_read(peer uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare
    me uuid := auth.uid();
begin
    if me is null then
        raise exception 'not signed in' using errcode = '28000';
    end if;
    if peer is null or peer = me then
        return;
    end if;

    update public.chat_messages
       set read_at = now()
     where recipient_id = me
       and sender_id = peer
       and read_at is null;
end;
$$;

create function public.set_chat_reaction(
    p_message_id uuid,
    p_emoji text default null,
    p_clear_emoji boolean default false,
    p_rating smallint default null,
    p_clear_rating boolean default false
) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
    me uuid := auth.uid();
    v_sender uuid;
    v_recipient uuid;
    cur_emoji text;
    cur_rating smallint;
    next_emoji text;
    next_rating smallint;
begin
    if me is null then
        raise exception 'not signed in' using errcode = '28000';
    end if;

    select m.sender_id, m.recipient_id
      into v_sender, v_recipient
      from public.chat_messages m
     where m.id = p_message_id;

    if v_sender is null or me not in (v_sender, v_recipient) then
        raise exception 'forbidden' using errcode = '42501';
    end if;

    select r.emoji, r.rating
      into cur_emoji, cur_rating
      from public.chat_reactions r
     where r.message_id = p_message_id
       and r.user_id = me;

    next_emoji := case
        when coalesce(p_clear_emoji, false) then null
        when p_emoji is not null then nullif(left(btrim(p_emoji), 16), '')
        else cur_emoji
    end;

    next_rating := case
        when coalesce(p_clear_rating, false) then null
        when p_rating is not null then p_rating
        else cur_rating
    end;

    if next_rating is not null and (next_rating < 1 or next_rating > 5) then
        raise exception 'invalid rating' using errcode = '22023';
    end if;

    if next_emoji is null and next_rating is null then
        delete from public.chat_reactions
         where message_id = p_message_id
           and user_id = me;
    else
        insert into public.chat_reactions (message_id, user_id, emoji, rating, updated_at)
        values (p_message_id, me, next_emoji, next_rating, now())
        on conflict (message_id, user_id) do update set
            emoji = excluded.emoji,
            rating = excluded.rating,
            updated_at = excluded.updated_at;
    end if;

    return coalesce((
        select jsonb_agg(jsonb_build_object(
            'user_id', r.user_id,
            'emoji', r.emoji,
            'rating', r.rating,
            'updated_at', r.updated_at
        ) order by r.updated_at desc)
        from public.chat_reactions r
        where r.message_id = p_message_id
    ), '[]'::jsonb);
end;
$$;

create function public.delete_chat_message(p_message_id uuid) returns void
language plpgsql security definer set search_path = '' as $$
declare
    me uuid := auth.uid();
begin
    if me is null then
        raise exception 'not signed in' using errcode = '28000';
    end if;

    delete from public.chat_messages
     where id = p_message_id
       and sender_id = me;
end;
$$;

revoke execute on function
    public.send_chat_item(uuid[], text, text, text, text, text, text, bigint, integer),
    public.chat_inbox(),
    public.chat_messages_with(uuid, integer),
    public.mark_chat_read(uuid),
    public.set_chat_reaction(uuid, text, boolean, smallint, boolean),
    public.delete_chat_message(uuid)
from public, anon;

grant execute on function
    public.send_chat_item(uuid[], text, text, text, text, text, text, bigint, integer),
    public.chat_inbox(),
    public.chat_messages_with(uuid, integer),
    public.mark_chat_read(uuid),
    public.set_chat_reaction(uuid, text, boolean, smallint, boolean),
    public.delete_chat_message(uuid)
to authenticated;

-- endregion

-- region Realtime

alter table public.chat_messages replica identity full;
alter table public.chat_reactions replica identity full;
alter publication supabase_realtime add table public.chat_messages, public.chat_reactions;

-- endregion
