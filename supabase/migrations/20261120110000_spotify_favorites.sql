-- Recognize Spotify tracks in the shared YouCloud favorites library.
alter table public.favorites
    drop constraint favorites_service_check,
    add constraint favorites_service_check
        check (service in ('soundcloud', 'yandex', 'youtube', 'spotify'));
