using import ..common radl.shorthands
import sdl ..wgpu ..window
ig := import ".bindings"

window-handle := state-accessor 'window 'handle
wgpu-device   := state-accessor 'renderer 'device

fn init ()
    ig.CreateContext null
    assert
        ig.ImplSDL2_InitForVulkan (window-handle)
    assert
        # FIXME: source these formats from a unified location
        ig.ImplWGPU_Init (wgpu-device) 3 'BGRA8UnormSrgb 'Undefined

    io := (ig.GetIO)
    ()

fn begin-frame ()
    ig.ImplSDL2_NewFrame;
    ig.ImplWGPU_NewFrame;
    ig.NewFrame;

fn reset-gpu-state ()
    ig.ImplWGPU_InvalidateDeviceObjects;
    ig.ImplWGPU_CreateDeviceObjects;
    ()

fn process-event (event)
    result := ig.ImplSDL2_ProcessEvent &event # do we even use this result for anything?
    io := (ig.GetIO)

    switch event.type
    pass sdl.SDL_MOUSEMOTION
    pass sdl.SDL_MOUSEBUTTONDOWN
    pass sdl.SDL_MOUSEBUTTONUP
    pass sdl.SDL_MOUSEWHEEL
    do
        deref io.WantCaptureMouse
    pass sdl.SDL_KEYDOWN
    pass sdl.SDL_KEYUP
    do
        deref io.WantCaptureKeyboard
    default false #result

fn render (render-pass render-size)
    ig.Render;
    draw-data := (ig.GetDrawData)
    draw-data.DisplaySize = ig.Vec2 (|> f32 (unpack render-size))
    ig.ImplWGPU_RenderDrawData draw-data render-pass

fn shutdown ()
    ig.ImplSDL2_Shutdown;
    ig.ImplWGPU_Shutdown;
    ig.Shutdown;

fn end-frame ()
    ig.EndFrame;

..
    do
        let init begin-frame reset-gpu-state process-event render shutdown end-frame
        local-scope;
    ig
