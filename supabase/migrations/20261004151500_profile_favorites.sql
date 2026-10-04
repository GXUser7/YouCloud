-- The profile's showcase: a favourite artist, track and album, each picked from a service's
-- search. Part of the profile, as the name and the avatar are: seen by whoever opens it.
-- Each is {"title", "subtitle", "cover", "link", "service"}; kept small.
alter table public.profiles
    add column fav_artist jsonb check (octet_length(fav_artist::text) <= 2048),
    add column fav_track jsonb check (octet_length(fav_track::text) <= 2048),
    add column fav_album jsonb check (octet_length(fav_album::text) <= 2048);

grant update (fav_artist, fav_track, fav_album) on public.profiles to authenticated;
