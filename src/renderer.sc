using import .common enum print radl.strfmt String
import .logger sdl .wgpu .window
from wgpu let chained@ typeinit@

cfg := state-accessor 'config 'renderer
ctx := state-accessor 'renderer
window-handle := state-accessor 'window 'handle

# INITIALIZATION
# ==============
enum WindowNativeInfo
    Windows : (hinstance = voidstar) (hwnd = voidstar)
    X11 : (display = voidstar) (window = u64)
    Wayland : (display = voidstar) (surface = voidstar)

fn get-native-info ()
    local wminfo : sdl.SysWMinfo
    sdl.SDL_VERSION &wminfo.version

    assert (storagecast (sdl.GetWindowWMInfo (window-handle) &wminfo))
    info subsystem := wminfo.info, wminfo.subsystem

    static-match operating-system
    case 'linux
        switch subsystem
        case 'SDL_SYSWM_X11
            WindowNativeInfo.X11 info.x11.display info.x11.window
        case 'SDL_SYSWM_WAYLAND
            WindowNativeInfo.Wayland info.wl.display info.wl.surface
        default
            logger.write-fatal f"Unsupported windowing system: ${subsystem}"
            abort;
    case 'windows
        assert (subsystem == 'SDL_SYSWM_WINDOWS)
        WindowNativeInfo.Windows info.win.hinstance info.win.window
    default
        static-error "OS not supported"

fn create-surface (instance)
    dispatch (get-native-info)
    case X11 (display window)
        wgpu.InstanceCreateSurface instance
            chained@ 'SurfaceDescriptorFromXlibWindow
                display = display
                window = typeinit window
    case Wayland (display surface)
        wgpu.InstanceCreateSurface instance
            chained@ 'SurfaceDescriptorFromWaylandSurface
                display = display
                surface = surface
    case Windows (hinstance hwnd)
        wgpu.InstanceCreateSurface instance
            chained@ 'SurfaceDescriptorFromWindowsHWND
                hinstance = hinstance
                hwnd = hwnd
    default
        abort;

fn request-adapter ()
    wgpu.InstanceRequestAdapter ctx.instance
        typeinit@
            compatibleSurface = ctx.surface
            powerPreference = 'HighPerformance
        fn (status adapter message userdata)
            if (status == 'Success)
                ctx.adapter = adapter
            else
                logger.write-fatal f"Could not create adapter. ${message}"
        null

fn request-device ()
    local device : wgpu.Device
    # TODO: requires further configuration
    wgpu.AdapterRequestDevice ctx.adapter
        typeinit@
            requiredLimits =
                typeinit@
                    limits = (wgpu.Limits)
        fn (status device message userdata)
            if (status == 'Success)
                ctx.device = device
            else
                logger.write-fatal f"Could not create device. ${message}"
        &device as voidstar

fn configure-surface ()
    width height := (window.get-size)
    wgpu.SurfaceConfigure ctx.surface
        typeinit@
            device = ctx.device
            usage = wgpu.TextureUsage.RenderAttachment
            format = 'BGRA8UnormSrgb
            width = u32 width
            height = u32 height
            presentMode = cfg.presentation-model

fn init ()
    wgpu.SetLogCallback
        fn (level message userdata)
            message := 'from-rawstring String message
            switch level
            case 'Error
                logger.write-fatal message
            case 'Warn
                logger.write-warning message
            case 'Info
                logger.write-info message
            case 'Debug
                logger.write-debug message
            case 'Trace
                print message
            default ()
        null
    wgpu.SetLogLevel cfg.log-level

    ctx.instance =
        wgpu.CreateInstance
            chained@ 'InstanceExtras
                backends = wgpu.InstanceBackend.Vulkan
                flags = wgpu.InstanceFlag.Debug

    ctx.surface = create-surface ctx.instance
    request-adapter;
    request-device;
    configure-surface;

    SystemLifetimeToken 'Renderer
        inline ()
            ctx = (typeinit)

# RENDERING
# ==========
fn acquire-surface-texture ()
    local surface-texture : wgpu.SurfaceTexture
    wgpu.SurfaceGetCurrentTexture ctx.surface &surface-texture

    if (surface-texture.status != 'Success)
        logger.write-debug f"The request for the surface texture was unsuccessful: ${surface-texture.status}"

    switch surface-texture.status
    case 'Success
        imply surface-texture.texture wgpu.Texture
    pass 'Timeout
    pass 'Outdated
    pass 'Lost
    do
        if (surface-texture.texture != null)
            wgpu.TextureRelease surface-texture.texture
        configure-surface;
        raise;
    default
        logger.write-fatal "Could not acquire surface texture: ${surface-texture.status}"
        abort;

fn present ()
    cmd-encoder := (wgpu.DeviceCreateCommandEncoder ctx.device (typeinit@))

    # TODO:
    # [x] resizing
    # [ ] change v-sync
    # [ ] minimize
    # [ ] MSAA
    let surface-texture =
        try (acquire-surface-texture)
        else (return)

    surface-texture-view := wgpu.TextureCreateView surface-texture null

    cmd-encoder :=
        wgpu.DeviceCreateCommandEncoder ctx.device (typeinit@)

    render-pass :=
        wgpu.CommandEncoderBeginRenderPass cmd-encoder
            typeinit@
                colorAttachmentCount = 1
                colorAttachments =
                    typeinit@
                        view = surface-texture-view
                        loadOp = 'Clear
                        storeOp = 'Store
                        clearValue = wgpu.Color 0.017 0.017 0.017 1.0

    wgpu.RenderPassEncoderEnd render-pass

    local cmd-buffer = wgpu.CommandEncoderFinish cmd-encoder null
    queue := wgpu.DeviceGetQueue ctx.device
    wgpu.QueueSubmit queue 1 (& (view cmd-buffer))
    wgpu.SurfacePresent ctx.surface

do
    let init present
    local-scope;
