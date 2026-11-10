// Обе копии фона используют одну фазу: затухание страницы не должно выдавать шов.
export const backdropMotion = $state({ turned: 0, mx: 0, my: 0 })
