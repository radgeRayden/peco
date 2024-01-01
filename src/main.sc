using import radl.strfmt radl.version-string String
import .logger sdl

PECO-VERSION := (git-version)
run-stage;

fn sdl-error ()
    'from-rawstring String (sdl.GetError)

fn main (argc argv)
    status :=
        sdl.Init
            | sdl.SDL_INIT_VIDEO sdl.SDL_INIT_TIMER sdl.SDL_INIT_GAMECONTROLLER

    if (status < 0)
        logger.write-fatal "SDL initialization failed." (sdl-error)
        abort;

    window-handle :=
        sdl.CreateWindow f"peco ${PECO-VERSION}"
            sdl.SDL_WINDOWPOS_UNDEFINED
            sdl.SDL_WINDOWPOS_UNDEFINED
            1280
            720
            0

    if (window-handle == null)
        logger.write-fatal "Could not create a window." (sdl-error)
        abort;

    local exit? : bool
    while (not exit?)
        local ev : sdl.Event
        sdl.PollEvent &ev

        switch ev.type
        case sdl.SDL_QUIT
            exit? = true
        default
            ()

    sdl.DestroyWindow window-handle
    sdl.Quit;
    0

main 0 0
