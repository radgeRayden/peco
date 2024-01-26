using import Array .common enum FunctionChain glm Option print \
    radl.shorthands radl.strfmt String
import .imgui .logger .resources sdl .wgpu .window
from wgpu let chained@ typeinit@

cfg := state-accessor 'config 'renderer
ctx := state-accessor 'renderer
window-handle := state-accessor 'window 'handle

fnchain on-imgui

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
fn... create-render-target (name, width, height, fmt, sample-count, bindable? : bool = false)
    width height := |> u32 width height
    let usage-flags =
        if bindable?
            wgpu.TextureUsage.RenderAttachment | wgpu.TextureUsage.TextureBinding
        else
            wgpu.TextureUsage.RenderAttachment

    wgpu.TextureCreateView
        wgpu.DeviceCreateTexture ctx.device
            typeinit@
                label = name
                usage = usage-flags
                dimension = '2D
                size = typeinit width height 1
                format = fmt
                mipLevelCount = 1
                sampleCount = u32 sample-count
        null

fn create-depth-buffer (width height)
    create-render-target "PECO depth buffer" width height DEPTH-FORMAT (cfg.msaa 4:u32 1:u32)

fn create-msaa-resolve-source (width height)
    create-render-target "PECO MSAA resolve src" width height SURFACE-FORMAT 4

fn... create-render-pipeline (vertex, fragment, msaa?, layout = none, depth? : bool = true)
    let layout =
        static-if (none? layout)
            wgpu.DeviceCreatePipelineLayout ctx.device
                typeinit@
                    label = "peco pip layout"
        else
            layout

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

    local depth-stencil-state : wgpu.DepthStencilState
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

    wgpu.DeviceCreateRenderPipeline ctx.device
        typeinit@
            label = "Peco Render Pipeline"
            layout = layout
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
                    count = msaa? 4:u32 1:u32
                    mask = ~0:u32
                    alphaToCoverageEnabled = false
            fragment =
                typeinit@
                    module = fragment
                    entryPoint = "main"
                    targetCount = 1
                    targets = &color-target
            depthStencil = (depth? &depth-stencil-state null)

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
            presentMode = cfg.present-mode

fn configure-renderbuffer ()
    configure-surface;

fn configure-internal-render-target (force?)
    :: render-target-outdated!

    height := (f32 ctx.surface-size.y) * ctx.resolution-scaling
    width := height * ctx.aspect-ratio

    if (ctx.main-render-target == null)
        merge render-target-outdated! width height

    cwidth cheight := |> f32 (unpack ctx.render-target-size)

    # if the size changed we need to recreate it.
    if (cwidth != width or cheight != height or force?)
        merge render-target-outdated! width height

    return;

    render-target-outdated! (width height) ::

    ctx.main-render-target =
        create-render-target "Internal Render Target" width height SURFACE-FORMAT 1 (bindable? = true)
    ctx.depth-stencil-attachment = create-depth-buffer width height
    if cfg.msaa
        ctx.msaa-resolve-source = create-msaa-resolve-source width height

    ctx.outdated-render-target? = true
    ()

fn get-available-present-modes ()
    local present-modes : (Array wgpu.PresentMode)
    local capabilities : wgpu.SurfaceCapabilities
    wgpu.SurfaceGetCapabilities ctx.surface ctx.adapter &capabilities

    for i in (range capabilities.presentModeCount)
        'append present-modes (capabilities.presentModes @ i)

    wgpu.SurfaceCapabilitiesFreeMembers capabilities
    present-modes

fn... set-present-mode (present-mode : wgpu.PresentMode)
    cfg.present-mode = present-mode
    ctx.outdated-surface? = true

fn... set-msaa (on? : bool)
    cfg.msaa = on?
    # FIXME: cache shaders and or pipelines
    try
        let vertex fragment =
            resources.get-shader (resources.load-shader S"shaders/default-vert.spv")
            resources.get-shader (resources.load-shader S"shaders/default-frag.spv")

        ctx.pipeline = create-render-pipeline vertex fragment on?
        configure-internal-render-target true
    else ()

fn... set-resolution-scaling (scale : f32, ratio : f32)
    if (scale != ctx.resolution-scaling or ratio != ctx.aspect-ratio)
        ctx.resolution-scaling = scale
        ctx.aspect-ratio = ratio

        configure-internal-render-target false
case (scale : f32)
    this-function scale (f32 ctx.aspect-ratio)

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

    configure-surface;
    ctx.resolution-scaling = cfg.resolution-scaling
    ctx.aspect-ratio = (16 / 9)

    try
        let vertex fragment =
            resources.get-shader (resources.load-shader S"shaders/default-vert.spv")
            resources.get-shader (resources.load-shader S"shaders/default-frag.spv")

        ctx.pipeline = create-render-pipeline vertex fragment cfg.msaa

        let vertex fragment =
            resources.get-shader (resources.load-shader S"shaders/scaled-output-vert.spv")
            resources.get-shader (resources.load-shader S"shaders/scaled-output-frag.spv")

        local bgroup-layout-entries =
            arrayof wgpu.BindGroupLayoutEntry
                typeinit
                    binding = 0
                    visibility = wgpu.ShaderStage.Fragment
                    sampler =
                        typeinit
                            type = 'Filtering
                typeinit
                    binding = 1
                    visibility = wgpu.ShaderStage.Fragment
                    texture =
                        typeinit
                            sampleType = 'Float
                            viewDimension = '2D

        ctx.scaled-output-bindgroup-layout =
            wgpu.DeviceCreateBindGroupLayout ctx.device
                typeinit@
                    label = "PECO bind group layout"
                    entryCount = (countof bgroup-layout-entries)
                    entries = (&bgroup-layout-entries as (@ wgpu.BindGroupLayoutEntry))

        local bgroup-layout =
            storagecast ctx.scaled-output-bindgroup-layout

        layout :=
            wgpu.DeviceCreatePipelineLayout ctx.device
                typeinit@
                    label = "PECO pipeline layout"
                    bindGroupLayoutCount = 1
                    bindGroupLayouts = (dupe &bgroup-layout)

        ctx.scaled-output-pipeline =
            create-render-pipeline vertex fragment false layout false
    else (abort)

    configure-internal-render-target false
    ctx.available-present-modes = (get-available-present-modes)

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

    if ctx.outdated-surface?
        imgui.reset-gpu-state;
        configure-renderbuffer;
        ctx.outdated-surface? = false
        return;

    if ctx.outdated-render-target?
        local bindgroup-entries =
            arrayof wgpu.BindGroupEntry
                typeinit
                    binding = 0
                    sampler =
                        wgpu.DeviceCreateSampler ctx.device
                            typeinit@
                                label = "PECO Sampler"
                                addressModeU = 'ClampToEdge
                                addressModeV = 'ClampToEdge
                                addressModeW = 'ClampToEdge
                                magFilter = 'Nearest
                                minFilter = 'Nearest
                                mipmapFilter = 'Linear
                                maxAnisotropy = 1
                typeinit
                    binding = 1
                    textureView = ctx.main-render-target
        ctx.scaled-output-bindgroup =
            wgpu.DeviceCreateBindGroup ctx.device
                typeinit@
                    label = "PECO Bind Group"
                    layout = ctx.scaled-output-bindgroup-layout
                    entryCount = 2
                    entries = &bindgroup-entries
        ctx.outdated-render-target? = false

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
                        view =
                            cfg.msaa ctx.msaa-resolve-source ctx.main-render-target
                        resolveTarget =
                            cfg.msaa ctx.main-render-target null
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

    render-pass :=
        wgpu.CommandEncoderBeginRenderPass cmd-encoder
            typeinit@
                colorAttachmentCount = 1
                colorAttachments =
                    typeinit@
                        view = surface-texture-view
                        loadOp = 'Clear
                        storeOp = 'Store
                        clearValue = wgpu.Color 0.0 0.0 0.0 0.0

    wgpu.RenderPassEncoderSetPipeline render-pass ctx.scaled-output-pipeline
    wgpu.RenderPassEncoderSetBindGroup render-pass 0 ctx.scaled-output-bindgroup 0 null
    wgpu.RenderPassEncoderDraw render-pass 6 1 0 0

    imgui.begin-frame;
    on-imgui;
    imgui.render render-pass ctx.surface-size
    wgpu.RenderPassEncoderEnd render-pass
    imgui.end-frame;

    local cmd-buffer = wgpu.CommandEncoderFinish cmd-encoder null
    queue := wgpu.DeviceGetQueue ctx.device
    wgpu.QueueSubmit queue 1 (& (view cmd-buffer))
    wgpu.SurfacePresent ctx.surface

do
    let init present set-present-mode set-msaa set-resolution-scaling
    let imgui = on-imgui
    local-scope;
