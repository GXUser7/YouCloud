-- Accounts and friends for YouCloud.
--
-- An account is a nick and a password. Supabase Auth signs in by email, so the app makes one
-- out of the nick (nick@youcloud.invalid, see the app's SupabaseConfig) and no mail ever goes
-- anywhere: "Confirm email" has to be off in the project's Auth settings. The nick, a name and an
-- avatar colour come along in the user's metadata, and the profile is made from them here.
--
-- Friends see what each other is playing (now_playing), as long as the one playing lets them
-- (profiles.share_listening). Everything a friend may do to a friendship goes through the
-- functions below, never straight at the table.

-- region Tables

create table public.profiles (
    id uuid primary key references auth.users on delete cascade,
    nick text not null unique check (nick ~ '^[a-z0-9_.]{3,20}$'),
    name text not null default '' check (char_length(name) <= 40),
    color text not null default '#9CC2A2' check (color ~ '^#[0-9A-Fa-f]{6}$'),
    -- Bumped with every new avatar: the picture's address carries it, so caches let go of the old one.
    avatar_v integer not null default 0,
    share_listening boolean not null default true,
    created_at timestamptz not null default now()
);

create table public.friendships (
    requester uuid not null references public.profiles on delete cascade,
    addressee uuid not null references public.profiles on delete cascade,
    status text not null default 'pending' check (status in ('pending', 'accepted')),
    created_at timestamptz not null default now(),
    primary key (requester, addressee),
    check (requester <> addressee)
);

-- One friendship a pair, whoever asked.
create unique index friendships_pair on public.friendships (least(requester, addressee), greatest(requester, addressee));
create index friendships_addressee on public.friendships (addressee);

-- What each one plays, one row each: written when the track changes or pauses, and every few
-- minutes while it plays, so a row long untouched means the phone went quiet.
create table public.now_playing (
    user_id uuid primary key references public.profiles on delete cascade,
    title text not null,
    artist text,
    cover_url text,
    -- soundcloud, youtube, yandex or device
    service text,
    -- The track's page, for a friend to play it too; none for a file on the phone.
    track_url text,
    playing boolean not null default true,
    -- When the track would have started, played without a break: where it is now follows.
    started_at timestamptz,
    duration_ms bigint,
    updated_at timestamptz not null default now()
);

-- Without an email, a lost password comes back only with the code shown at sign-up. Only its
-- hash is kept, and only the recover function reads it.
create table public.recovery_codes (
    user_id uuid primary key references auth.users on delete cascade,
    code_hash text not null,
    updated_at timestamptz not null default now()
);

-- endregion

-- region The profile, made with the account

create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    wanted text := lower(new.raw_user_meta_data ->> 'nick');
begin
    -- The account is the nick's: an address made of another nick can't take this one.
    if wanted is null or coalesce(split_part(new.email, '@', 1), '') <> wanted then
        raise exception 'nick and email differ' using errcode = '22023';
    end if;
    insert into public.profiles (id, nick, name, color)
    values (
        new.id,
        wanted,
        coalesce(nullif(btrim(new.raw_user_meta_data ->> 'name'), ''), wanted),
        coalesce(new.raw_user_meta_data ->> 'color', '#9CC2A2')
    );
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- endregion

-- region Row level security

alter table public.profiles enable row level security;
alter table public.friendships enable row level security;
alter table public.now_playing enable row level security;
alter table public.recovery_codes enable row level security;

create function public.are_friends(a uuid, b uuid) returns boolean
language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.friendships f
        where f.status = 'accepted'
          -- Only about the one asking: nobody learns who else is friends with whom.
          and auth.uid() in (a, b)
          and ((f.requester = a and f.addressee = b) or (f.requester = b and f.addressee = a))
    );
$$;

-- Nicks, names and avatars are what search finds: open to anyone signed in.
create policy "profiles are seen by the signed in" on public.profiles
    for select to authenticated using (true);
create policy "own profile is changed by its owner" on public.profiles
    for update to authenticated
    using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy "own friendships are seen" on public.friendships
    for select to authenticated using ((select auth.uid()) in (requester, addressee));

create policy "own track and friends' tracks are seen" on public.now_playing
    for select to authenticated using (
        user_id = (select auth.uid())
        or (
            public.are_friends((select auth.uid()), user_id)
            and exists (select 1 from public.profiles p where p.id = user_id and p.share_listening)
        )
    );
create policy "own track is written" on public.now_playing
    for insert to authenticated with check (user_id = (select auth.uid()));
create policy "own track is updated" on public.now_playing
    for update to authenticated
    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "own track is cleared" on public.now_playing
    for delete to authenticated using (user_id = (select auth.uid()));

-- Nobody signed out reads anything; the nick is never changed (it is the login), nor is a
-- profile made or removed but with its account; friendships change only through the functions.
revoke all on public.profiles, public.friendships, public.now_playing, public.recovery_codes from anon;
revoke insert, update, delete on public.profiles from authenticated;
grant update (name, color, avatar_v, share_listening) on public.profiles to authenticated;
revoke insert, update, delete on public.friendships from authenticated;
revoke all on public.recovery_codes from authenticated;

-- endregion

-- region Functions the app calls

-- Whether a nick is still free: asked while it is typed, before there is an account.
create function public.nick_available(candidate text) returns boolean
language sql stable security definer set search_path = '' as $$
    select not exists (select 1 from public.profiles p where p.nick = lower(candidate));
$$;

-- What one person is to the one asking: self, friend, outgoing (asked by me), incoming or none.
create function public.relation_to(target uuid) returns text
language sql stable security definer set search_path = '' as $$
    select case
        when target = auth.uid() then 'self'
        when f.status = 'accepted' then 'friend'
        when f.requester = auth.uid() then 'outgoing'
        when f.addressee = auth.uid() then 'incoming'
        else 'none'
    end
    from (select 1) one
    left join public.friendships f
        on (f.requester = auth.uid() and f.addressee = target)
        or (f.requester = target and f.addressee = auth.uid());
$$;

-- Friends with what they play, and the requests either way: the friends screen in one call.
create function public.friends_overview()
returns table (
    id uuid, nick text, name text, color text, avatar_v integer, relation text,
    title text, artist text, cover_url text, service text, track_url text,
    playing boolean, started_at timestamptz, duration_ms bigint, updated_at timestamptz
)
language sql stable security definer set search_path = '' as $$
    select
        p.id, p.nick, p.name, p.color, p.avatar_v,
        case
            when f.status = 'accepted' then 'friend'
            when f.requester = auth.uid() then 'outgoing'
            else 'incoming'
        end,
        n.title, n.artist, n.cover_url, n.service, n.track_url,
        n.playing, n.started_at, n.duration_ms, n.updated_at
    from public.friendships f
    join public.profiles p
        on p.id = case when f.requester = auth.uid() then f.addressee else f.requester end
    left join public.now_playing n
        on n.user_id = p.id and f.status = 'accepted' and p.share_listening
    where auth.uid() in (f.requester, f.addressee)
    order by n.updated_at desc nulls last, p.nick;
$$;

-- One profile as its page shows it: who, how many friends, and, for a friend, what plays.
create function public.profile_card(target uuid)
returns table (
    id uuid, nick text, name text, color text, avatar_v integer, relation text, friends bigint,
    title text, artist text, cover_url text, service text, track_url text,
    playing boolean, started_at timestamptz, duration_ms bigint, updated_at timestamptz
)
language sql stable security definer set search_path = '' as $$
    select
        p.id, p.nick, p.name, p.color, p.avatar_v, r.relation,
        (select count(*) from public.friendships x
            where x.status = 'accepted' and target in (x.requester, x.addressee)),
        n.title, n.artist, n.cover_url, n.service, n.track_url,
        n.playing, n.started_at, n.duration_ms, n.updated_at
    from public.profiles p
    cross join (select public.relation_to(target) as relation) r
    left join public.now_playing n
        on n.user_id = p.id and p.share_listening and r.relation in ('friend', 'self')
    where p.id = target and auth.uid() is not null;
$$;

-- People by the first letters of their nick or name, with what each is to the one searching.
create function public.search_profiles(q text)
returns table (id uuid, nick text, name text, color text, avatar_v integer, relation text)
language sql stable security definer set search_path = '' as $$
    select p.id, p.nick, p.name, p.color, p.avatar_v, public.relation_to(p.id)
    from public.profiles p
    where auth.uid() is not null
      and p.id <> auth.uid()
      and char_length(btrim(q)) > 0
      and (
          p.nick like replace(replace(lower(btrim(q, ' @')), '_', '\_'), '%', '\%') || '%'
          or lower(p.name) like replace(replace(lower(btrim(q)), '_', '\_'), '%', '\%') || '%'
      )
    order by p.nick = lower(btrim(q, ' @')) desc, p.nick
    limit 20;
$$;

-- Asks [target] to be friends; a request from them already waiting is accepted instead.
-- Answers what [target] is to the caller afterwards.
create function public.request_friend(target uuid) returns text
language plpgsql security definer set search_path = '' as $$
declare
    me uuid := auth.uid();
begin
    if me is null then raise exception 'not signed in' using errcode = '28000'; end if;
    if target = me then raise exception 'that is you' using errcode = '22023'; end if;
    update public.friendships set status = 'accepted'
        where requester = target and addressee = me and status = 'pending';
    if not found then
        insert into public.friendships (requester, addressee) values (me, target)
            on conflict do nothing;
    end if;
    return public.relation_to(target);
end;
$$;

-- Accepts [target]'s request.
create function public.accept_friend(target uuid) returns text
language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'not signed in' using errcode = '28000'; end if;
    update public.friendships set status = 'accepted'
        where requester = target and addressee = auth.uid() and status = 'pending';
    return public.relation_to(target);
end;
$$;

-- Ends whatever is between the caller and [target]: a friendship, or a request either way.
create function public.remove_friend(target uuid) returns text
language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'not signed in' using errcode = '28000'; end if;
    delete from public.friendships
        where (requester = auth.uid() and addressee = target)
           or (requester = target and addressee = auth.uid());
    return public.relation_to(target);
end;
$$;

-- Keeps the hash of the caller's new recovery code (the code itself never leaves the phone).
create function public.set_recovery_code(hash text) returns void
language plpgsql security definer set search_path = '' as $$
begin
    if auth.uid() is null then raise exception 'not signed in' using errcode = '28000'; end if;
    if hash !~ '^[0-9a-f]{64}$' then raise exception 'not a hash' using errcode = '22023'; end if;
    insert into public.recovery_codes (user_id, code_hash) values (auth.uid(), hash)
        on conflict (user_id) do update set code_hash = excluded.code_hash, updated_at = now();
end;
$$;

revoke execute on all functions in schema public from public, anon;
-- A trigger's, never to be called through the API.
revoke execute on function public.handle_new_user() from authenticated;
grant execute on function public.nick_available(text) to anon, authenticated;
grant execute on function
    public.relation_to(uuid),
    public.friends_overview(),
    public.profile_card(uuid),
    public.search_profiles(text),
    public.request_friend(uuid),
    public.accept_friend(uuid),
    public.remove_friend(uuid),
    public.set_recovery_code(text)
    to authenticated;
-- Used inside the policies above, as whoever reads.
grant execute on function public.are_friends(uuid, uuid) to authenticated;

-- endregion

-- region Avatars

-- 256 px WebP the app makes itself, at a few tens of kilobytes: one folder a person, theirs alone.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 262144, array['image/webp', 'image/jpeg', 'image/png'])
on conflict (id) do nothing;

create policy "avatars: own folder is read" on storage.objects
    for select to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid()::text));
create policy "avatars: own folder is written" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid()::text));
create policy "avatars: own folder is replaced" on storage.objects
    for update to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid()::text))
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid()::text));
create policy "avatars: own folder is cleared" on storage.objects
    for delete to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid()::text));

-- endregion
