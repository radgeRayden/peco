using import Array .common enum print radl.IO.FileStream radl.strfmt String struct switcher
import .config .imgui .logger .renderer .resources sdl .window wgpu

spice collect-enum-fields (ET)
    using import Array radl.String+

    ET as:= type
    local args : (Array Symbol)
    for k v in ('symbols ET)
        if (not (starts-with? (String (k as string)) "_"))
            'append args k

    sc_argument_list_map_new (i32 (countof args))
        inline (i)
            arg := args @ i
            `arg
run-stage;

@@ memo
inline collect-enum-fields (ET)
    collect-enum-fields ET

@@ 'on logger.write
inline (...)
    print2 ...

struct DebugWindowState
    open? : bool = true

global dbg-state : DebugWindowState

inline combo-from-enum-choices (name source choices applyf)
    ig := imgui
    ET := (unqualified source)
    static-assert (ET < CEnum)
    EF... := collect-enum-fields ET
    static-assert ((va-countof EF...) > 0)

    local item-idx : i32
    for i element in (enumerate choices)
        if (element == source)
            item-idx = i
            break;

    switcher match-name
        va-map
            inline (s)
                case (getattr ET s)
                    (static-eval (s as string)) as rawstring
            EF...
        default
            _ &"?" ()

    data ptr := 'data choices
    count := (countof choices) as i32
    max-height := max count 4

    vvv bind result
    ig.Combo_FnStrPtr name &item-idx
        fn (userdata idx)
            element := (userdata as (@ ET)) @ idx
            match-name element
        (view data) as voidstar
        count
        max-height

    if result
        applyf (choices @ item-idx)

@@ 'on renderer.imgui
fn ()
    ui := dbg-state
    ig := imgui
    cfg := state-accessor 'config
    ctx := (state-accessor)

    if ui.open?
        ig.Begin "Debug" &ui.open? 0
        combo-from-enum-choices "Present Mode" cfg.renderer.present-mode
            ctx.renderer.available-present-modes
            (v) -> (renderer.set-present-mode v)
        if (ig.Checkbox "MSAA enabled" &cfg.renderer.msaa)
            renderer.set-msaa cfg.renderer.msaa

        # Post Processing
        # Internal Resolution
        local scale = (f32 ctx.renderer.resolution-scaling)
        if (ig.SliderFloat "Resolution Scaling" &scale 0.0 1.0 "%.2f" 0)
            renderer.set-resolution-scaling scale

        ig.End;

fn main (argc argv)
    config.init;
    window.init;
    renderer.init;
    imgui.init;

    local exit? : bool
    while (not exit?)
        local ev : sdl.Event
        while (sdl.PollEvent &ev)
            if (imgui.process-event ev)
                continue;

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
