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

    inline window-flags (flags...)
        va-lfold 0:u32
            inline (k next result)
                setting := getattr cfg k
                if setting
                    result | next
                else
                    result
            flags...

    window-handle :=
        sdl.CreateWindow cfg.title
            sdl.SDL_WINDOWPOS_UNDEFINED
            sdl.SDL_WINDOWPOS_UNDEFINED
            i32 cfg.width
            i32 cfg.height
            window-flags
                fullscreen = sdl.SDL_WINDOW_FULLSCREEN_DESKTOP
                hidden = sdl.SDL_WINDOW_HIDDEN
                borderless = sdl.SDL_WINDOW_BORDERLESS
                resizable = sdl.SDL_WINDOW_RESIZABLE
                minimized = sdl.SDL_WINDOW_MINIMIZED
                maximized = sdl.SDL_WINDOW_MAXIMIZED
                always-on-top = sdl.SDL_WINDOW_ALWAYS_ON_TOP

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
