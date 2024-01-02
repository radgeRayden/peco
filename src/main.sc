using import enum radl.IO.FileStream radl.strfmt radl.version-string String
import .config .logger sdl wgpu

PECO-VERSION := (git-version)
run-stage;

inline typeinit@ (...)
    implies (T)
        static-assert (T < pointer)
        imply (& (local (elementof T) ...)) T

inline chained@ (K ...)
    using wgpu
    chaintypename := K
    K := getattr wgpu K
    chaintype := static-try (getattr SType chaintypename)
    else
        (getattr NativeSType chaintypename) as (storageof SType) as SType
    typeinit@
        nextInChain = as
            &
                local K
                    chain = typeinit
                        sType = chaintype
                    ...
            mutable@ ChainedStruct

enum WindowNativeInfo
    Windows : (hinstance = voidstar) (hwnd = voidstar)
    X11 : (display = voidstar) (window = u64)
    Wayland : (display = voidstar) (surface = voidstar)

fn get-native-info (window)
    local wminfo : sdl.SysWMinfo
    sdl.SDL_VERSION &wminfo.version

    assert (storagecast (sdl.GetWindowWMInfo window &wminfo))
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

fn create-surface (instance window)
    dispatch (get-native-info window)
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

fn sdl-error ()
    'from-rawstring String (sdl.GetError)

fn acquire-surface-texture (surface)
    local surface-texture : wgpu.SurfaceTexture
    wgpu.SurfaceGetCurrentTexture surface &surface-texture

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
        # configure-surface;

        # raise GPUError.DiscardedFrame
        abort;
    default
        logger.write-fatal "Could not acquire surface texture: ${surface-texture.status}"
        abort;

fn main (argc argv)
    # read config
    let cfg =
        try
            fs := FileStream "config.toml" FileMode.Read
            'read-all-string fs
        then (cfg-str)
            config.parse cfg-str
        else
            config.default;

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
            i32 cfg.window.width
            i32 cfg.window.height
            0

    if (window-handle == null)
        logger.write-fatal "Could not create a window." (sdl-error)
        abort;

    instance :=
        wgpu.CreateInstance
            chained@ 'InstanceExtras
                backends = wgpu.InstanceBackend.Vulkan
                flags = wgpu.InstanceFlag.Debug

    surface := create-surface instance window-handle

    local adapter : wgpu.Adapter
    wgpu.InstanceRequestAdapter instance
        typeinit@
            compatibleSurface = surface
            powerPreference = 'HighPerformance
        fn (status adapter message userdata)
            if (status == 'Success)
                (@ (userdata as (mutable@ wgpu.Adapter))) = adapter
            else
                logger.write-fatal f"Could not create adapter. ${message}"
        &adapter as voidstar

    local device : wgpu.Device
    # TODO: requires further configuration
    wgpu.AdapterRequestDevice adapter
        typeinit@
            requiredLimits =
                typeinit@
                    limits =
                        wgpu.Limits
                            maxTextureDimension1D = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxTextureDimension2D = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxTextureDimension3D = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxTextureArrayLayers = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxBindGroups = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxBindingsPerBindGroup = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxDynamicUniformBuffersPerPipelineLayout = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxDynamicStorageBuffersPerPipelineLayout = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxSampledTexturesPerShaderStage = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxSamplersPerShaderStage = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxStorageBuffersPerShaderStage = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxStorageTexturesPerShaderStage = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxUniformBuffersPerShaderStage = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxUniformBufferBindingSize = wgpu.WGPU_LIMIT_U64_UNDEFINED
                            maxStorageBufferBindingSize = wgpu.WGPU_LIMIT_U64_UNDEFINED
                            minUniformBufferOffsetAlignment = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            minStorageBufferOffsetAlignment = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxVertexBuffers = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxBufferSize = wgpu.WGPU_LIMIT_U64_UNDEFINED
                            maxVertexAttributes = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxVertexBufferArrayStride = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxInterStageShaderComponents = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxInterStageShaderVariables = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxColorAttachments = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxColorAttachmentBytesPerSample = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxComputeWorkgroupStorageSize = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxComputeInvocationsPerWorkgroup = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxComputeWorkgroupSizeX = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxComputeWorkgroupSizeY = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxComputeWorkgroupSizeZ = wgpu.WGPU_LIMIT_U32_UNDEFINED
                            maxComputeWorkgroupsPerDimension = wgpu.WGPU_LIMIT_U32_UNDEFINED
        fn (status device message userdata)
            if (status == 'Success)
                (@ (userdata as (mutable@ wgpu.Device))) = device
            else
                logger.write-fatal f"Could not create device. ${message}"
        &device as voidstar

    # TODO: error callbacks
    wgpu.SurfaceConfigure surface
        typeinit@
            device = device
            usage = wgpu.TextureUsage.RenderAttachment
            format = 'BGRA8UnormSrgb
            width = u32 cfg.window.width
            height = u32 cfg.window.height
            presentMode = 'FifoRelaxed

    queue := wgpu.DeviceGetQueue device

    local exit? : bool
    while (not exit?)
        local ev : sdl.Event
        sdl.PollEvent &ev

        switch ev.type
        case sdl.SDL_QUIT
            exit? = true
        default
            ()

        cmd-encoder := (wgpu.DeviceCreateCommandEncoder device (typeinit@))

        # TODO:
        # [ ] resizing
        # [ ] change v-sync
        # [ ] minimize
        # [ ] MSAA
        surface-texture := (acquire-surface-texture surface)
        surface-texture-view := wgpu.TextureCreateView surface-texture null

        cmd-encoder :=
            wgpu.DeviceCreateCommandEncoder device (typeinit@)

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
        wgpu.QueueSubmit queue 1 &cmd-buffer
        wgpu.SurfacePresent surface

    sdl.DestroyWindow window-handle
    sdl.Quit;
    0

main 0 0
