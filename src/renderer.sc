using import Array .common enum glm print radl.shorthands radl.strfmt String
import .imgui .logger .resources sdl .wgpu .window
from wgpu let chained@ typeinit@

cfg := state-accessor 'config 'renderer
ctx := state-accessor 'renderer
window-handle := state-accessor 'window 'handle

SURFACE-FORMAT := wgpu.TextureFormat.BGRA8UnormSrgb
DEPTH-FORMAT := wgpu.TextureFormat.Depth32FloatStencil8
REQUIRED-FEATURES :=
    arrayof wgpu.FeatureName
        'Depth32FloatStencil8

OPTIONAL-FEATURES := none

inline wgpu-array-query (f args...)
    T@ := elementof (typeof f) (va-countof args...)
    T := elementof T@ 0

    local result : (Array T)
    count := f ((va-join args...) null)
    'resize result count

    ptr := 'data result
    f ((va-join args...) ptr)
    result

# RESOURCE CREATION
# =================
fn create-depth-buffer (width height)
    width height := |> u32 width height
    wgpu.TextureCreateView
        wgpu.DeviceCreateTexture ctx.device
            typeinit@
                label = "depth buffer"
                usage = wgpu.TextureUsage.RenderAttachment
                dimension = '2D
                size = typeinit width height 1
                format = DEPTH-FORMAT
                mipLevelCount = 1
                sampleCount = cfg.msaa 4:u32 1:u32
        null

fn create-msaa-resolve-source (width height)
    width height := |> u32 width height
    wgpu.TextureCreateView
        wgpu.DeviceCreateTexture ctx.device
            typeinit@
                label = "MSAA resolve source"
                usage = wgpu.TextureUsage.RenderAttachment
                dimension = '2D
                size = typeinit width height 1
                format = SURFACE-FORMAT
                mipLevelCount = 1
                sampleCount = 4
        null

fn create-render-pipeline (vertex fragment)
    local color-target : wgpu.ColorTargetState
        format = SURFACE-FORMAT
        blend =
            typeinit@
                color =
                    wgpu.BlendComponent
                        operation = 'Add
                        srcFactor = 'SrcAlpha
                        dstFactor = 'OneMinusSrcAlpha
                alpha =
                    wgpu.BlendComponent
                        operation = 'Add
                        srcFactor = 'One
                        dstFactor = 'OneMinusSrcAlpha
        writeMask = wgpu.ColorWriteMask.All

    wgpu.DeviceCreateRenderPipeline ctx.device
        typeinit@
            label = "Peco Render Pipeline"
            layout =
                wgpu.DeviceCreatePipelineLayout ctx.device
                    typeinit@
                        label = "peco pip layout"
            vertex =
                typeinit
                    module = vertex
                    entryPoint = "main"
            primitive =
                wgpu.PrimitiveState
                    topology = 'TriangleList
                    frontFace = 'CCW
                    cullMode = 'Back
            multisample =
                wgpu.MultisampleState
                    count = cfg.msaa 4:u32 1:u32
                    mask = ~0:u32
                    alphaToCoverageEnabled = false
            fragment =
                typeinit@
                    module = fragment
                    entryPoint = "main"
                    targetCount = 1
                    targets = &color-target
            depthStencil =
                typeinit@
                    format = DEPTH-FORMAT
                    depthWriteEnabled = true
                    depthCompare = 'Less
                    stencilFront =
                        typeinit
                            compare = 'Always
                            failOp = 'Zero
                            depthFailOp = 'Zero
                            passOp = 'Zero
                    stencilBack =
                        typeinit
                            compare = 'Always
                            failOp = 'Zero
                            depthFailOp = 'Zero
                            passOp = 'Zero
                    # FIXME: depth bias stuff missing

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

fn query-adapters ()
    adapters := wgpu-array-query wgpu.InstanceEnumerateAdapters ctx.instance null
    for adapter in adapters
        local properties : wgpu.AdapterProperties
        wgpu.AdapterGetProperties adapter &properties

        local limits : wgpu.SupportedLimits
        wgpu.AdapterGetLimits adapter &limits

        supported-features := wgpu-array-query wgpu.AdapterEnumerateFeatures adapter

        'emplace-append ctx.available-adapters adapter properties limits supported-features

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
    # TODO: requires further configuration
    local required-features = REQUIRED-FEATURES
    wgpu.AdapterRequestDevice ctx.adapter
        typeinit@
            requiredLimits =
                typeinit@
                    limits = (wgpu.Limits)
            requiredFeatureCount = (countof required-features)
            requiredFeatures = &required-features
        fn (status device message userdata)
            if (status == 'Success)
                ctx.device = device
            else
                logger.write-fatal f"Could not create device. ${message}"
        null

fn configure-surface ()
    ww wh := (window.get-size)
    ctx.surface-size = ivec2 ww wh
    wgpu.SurfaceConfigure ctx.surface
        typeinit@
            device = ctx.device
            usage = wgpu.TextureUsage.RenderAttachment
            format = SURFACE-FORMAT
            width = u32 ww
            height = u32 wh
            presentMode = cfg.presentation-model

fn configure-renderbuffer ()
    ctx.depth-stencil-attachment = (create-depth-buffer (window.get-size))
    if cfg.msaa
        ctx.msaa-resolve-source = (create-msaa-resolve-source (window.get-size))
    configure-surface;

fn get-available-present-modes ()
    local present-modes : (Array wgpu.PresentMode)
    local capabilities : wgpu.SurfaceCapabilities
    wgpu.SurfaceGetCapabilities ctx.surface ctx.adapter ('data capabilities) ()
    wgpu.SurfaceCapabilitiesFreeMembers capabilities

fn... set-present-mode (present-mode : wgpu.PresentMode)
    cfg.presentation-model = present-mode
    ctx.requires-reconfiguration? = true

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

    query-adapters;

    ctx.surface = create-surface ctx.instance
    request-adapter;
    request-device;

    wgpu.DeviceSetUncapturedErrorCallback ctx.device
        fn (err message userdata)
            msgstr := () -> ('from-rawstring String message)

            switch err
            pass 'Validation
            pass 'OutOfMemory
            pass 'Internal
            pass 'Unknown
            pass 'DeviceLost
            do
                logger.write-fatal "\n" (msgstr)
                abort;
            default
                ()
        null

    configure-renderbuffer;
    try
        let vertex fragment =
            resources.get-shader (resources.load-shader S"shaders/default-vert.spv")
            resources.get-shader (resources.load-shader S"shaders/default-frag.spv")

        ctx.pipeline = create-render-pipeline vertex fragment
    else ()

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
        configure-renderbuffer;
        raise;
    default
        logger.write-fatal "Could not acquire surface texture: ${surface-texture.status}"
        abort;

global demo-window : bool = true

fn present ()
    cmd-encoder := (wgpu.DeviceCreateCommandEncoder ctx.device (typeinit@))

    if ctx.requires-reconfiguration?
        imgui.reset-gpu-state;
        configure-renderbuffer;
        ctx.requires-reconfiguration? = false
        return;

    let surface-texture =
        try (acquire-surface-texture)
        else (return)
    imgui.begin-frame;

    surface-texture-view := wgpu.TextureCreateView surface-texture null

    cmd-encoder :=
        wgpu.DeviceCreateCommandEncoder ctx.device (typeinit@)

    render-pass :=
        wgpu.CommandEncoderBeginRenderPass cmd-encoder
            typeinit@
                colorAttachmentCount = 1
                colorAttachments =
                    typeinit@
                        view =
                            cfg.msaa ctx.msaa-resolve-source surface-texture-view
                        resolveTarget =
                            cfg.msaa surface-texture-view null
                        loadOp = 'Clear
                        storeOp = 'Store
                        clearValue = wgpu.Color 0.017 0.017 0.017 1.0
                depthStencilAttachment =
                    typeinit@
                        view = ctx.depth-stencil-attachment
                        depthLoadOp = 'Clear
                        depthStoreOp = 'Store
                        depthClearValue = 1.0
                        depthReadOnly = false
                        stencilLoadOp = 'Clear
                        stencilStoreOp = 'Store
                        stencilClearValue = 0
                        stencilReadOnly = false

    wgpu.RenderPassEncoderSetPipeline render-pass ctx.pipeline
    wgpu.RenderPassEncoderDraw render-pass 3 1 0 0

    wgpu.RenderPassEncoderEnd render-pass

    if demo-window
        imgui.ShowDemoWindow &demo-window

    render-pass :=
        wgpu.CommandEncoderBeginRenderPass cmd-encoder
            typeinit@
                colorAttachmentCount = 1
                colorAttachments =
                    typeinit@
                        view = surface-texture-view
                        loadOp = 'Load
                        storeOp = 'Store
    imgui.render render-pass ctx.surface-size
    wgpu.RenderPassEncoderEnd render-pass
    imgui.end-frame;

    local cmd-buffer = wgpu.CommandEncoderFinish cmd-encoder null
    queue := wgpu.DeviceGetQueue ctx.device
    wgpu.QueueSubmit queue 1 (& (view cmd-buffer))
    wgpu.SurfacePresent ctx.surface

do
    let init present set-present-mode
    local-scope;
