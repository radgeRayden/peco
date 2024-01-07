using import print String struct radl.version-string
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
            presentation-model : wgpu.PresentMode
            log-level : wgpu.LogLevel

struct PecoWindowState
    handle : (mutable@ sdl.Window)

struct PecoRendererState
    instance : wgpu.Instance
    surface : wgpu.Surface
    adapter : wgpu.Adapter
    device : wgpu.Device

struct PecoState
    config : PecoConfig
    window : PecoWindowState
    renderer : PecoRendererState

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
