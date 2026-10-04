-- What friends play, the friendships and the profiles, as they change, to the screens open on
-- them (Supabase Realtime). Each subscriber is told only of the rows its own row rules let it
-- see: a friend's track, its own friendships, the profiles anyone signed in may read.
alter publication supabase_realtime add table public.now_playing, public.friendships, public.profiles;
