using import .common enum print radl.IO.FileStream radl.strfmt String
import .config .logger .renderer .resources sdl .window wgpu

@@ 'on logger.on-log
inline (...)
    print2 ...

fn main (argc argv)
    config.init;
    window.init;
    renderer.init;

    try (print (resources.load-shader S"shaders/default-vert.spv"))
    else ()

    local exit? : bool
    while (not exit?)
        local ev : sdl.Event
        while (sdl.PollEvent &ev)
            switch ev.type
            case sdl.SDL_QUIT
                exit? = true
            case sdl.SDL_KEYDOWN ()
            case sdl.SDL_KEYUP
                k := ev.key.keysym.sym
                if (k == sdl.SDLK_SPACE)
                    renderer.set-present-mode 'Immediate
            default
                ()

        renderer.present;
    0

main 0 0
