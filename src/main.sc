using import .common enum print radl.IO.FileStream radl.strfmt String
import .config .logger .renderer sdl .window wgpu

@@ 'on logger.on-log
fn (...)
    print2 ...

fn main (argc argv)
    config.init;
    window.init;
    renderer.init;

    local exit? : bool
    while (not exit?)
        local ev : sdl.Event
        while (sdl.PollEvent &ev)
            switch ev.type
            case sdl.SDL_QUIT
                exit? = true
            default
                ()

        renderer.present;
    0

main 0 0
