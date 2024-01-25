using import Array glm Map print String struct radl.ArrayMap radl.version-string
import .logger sdl .wgpu

PECO-VERSION := (git-version)
run-stage;

struct PecoConfig
    window :
        struct PecoWindowConfig
            title : String
            width : i64
            height : i64
            fullscreen : bool
            hidden : bool
            borderless : bool
            resizable : bool
            minimized : bool
            maximized : bool
            always-on-top : bool
    renderer :
        struct PecoRendererConfig
            present-mode : wgpu.PresentMode
            log-level : wgpu.LogLevel
            msaa : bool

struct PecoWindowState
    handle : (mutable@ sdl.Window)

struct WGPUAdapterInfo
    adapter : wgpu.Adapter
    properties : wgpu.AdapterProperties
    limits : wgpu.SupportedLimits
    supported-features : (Array wgpu.FeatureName)

struct PecoRendererState
    available-adapters : (Array WGPUAdapterInfo)
    instance : wgpu.Instance
    surface : wgpu.Surface
    adapter : wgpu.Adapter
    device : wgpu.Device

    available-present-modes : (Array wgpu.PresentMode)

    surface-size : ivec2
    depth-stencil-attachment : wgpu.TextureView
    msaa-resolve-source : wgpu.TextureView
    requires-reconfiguration? : bool

    pipeline : wgpu.RenderPipeline

struct PecoResourceManager
    inline resource-map (T)
        AT := ArrayMap T
        struct (.. "ResourceMap<" (static-tostring T) ">")
            elements : AT
            mapping : (Map String AT.IndexType)

    shaders : (resource-map wgpu.ShaderModule)

    unlet resource-map

struct PecoState
    config : PecoConfig
    window : PecoWindowState
    renderer : PecoRendererState
    resources : PecoResourceManager

global state : PecoState

@@ memo
inline state-accessor (chain...)
    name := static-eval (('unique Symbol "PecoStateAccessor") as string)
    type (_ name)
        inline __typeattr (cls attr)
            getattr
                va-lfold state
                    inline (?? next computed)
                        getattr computed next
                    chain...
                attr

        inline __typecall (cls)
            va-lfold state
                inline (?? next computed)
                    getattr computed next
                chain...

inline get-version ()
    PECO-VERSION

spice SystemLifetimeToken (name dropf)
    anchor := 'anchor args
    name := name as Symbol as string
    qq
        [do]
            [let] [('unique Symbol name)] =
                [typedef] ([..] "SystemLifetimeToken<" [name] ">") :: (tuple)
                    [inline] __drop (self)
                        [logger.write-info] "System shutdown:" [name]

            [logger.write-info@] [anchor] "System initialized:" [name]
            [bitcast] none T

do
    let PecoConfig SystemLifetimeToken
    let get-version state-accessor
    local-scope;
