using import .common String
import .logger sdl

cfg := state-accessor 'config 'window
ctx := state-accessor 'window

fn sdl-error ()
    'from-rawstring String (sdl.GetError)

fn init ()
    status :=
        sdl.Init
            | sdl.SDL_INIT_VIDEO sdl.SDL_INIT_TIMER sdl.SDL_INIT_GAMECONTROLLER

    if (status < 0)
        logger.write-fatal "SDL initialization failed." (sdl-error)
        abort;

    window-handle :=
        sdl.CreateWindow cfg.title
            sdl.SDL_WINDOWPOS_UNDEFINED
            sdl.SDL_WINDOWPOS_UNDEFINED
            i32 cfg.width
            i32 cfg.height
            0

    if (window-handle == null)
        logger.write-fatal "Could not create a window." (sdl-error)
        abort;

    ctx.handle = window-handle

    SystemLifetimeToken 'Window
        inline ()
            sdl.DestroyWindow ctx.handle
            ctx = (typeinit)

fn get-size ()
    local width : i32
    local height : i32
    sdl.GetWindowSize ctx.handle &width &height
    _ width height

do
    let init get-size
    local-scope;
