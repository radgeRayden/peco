using import String struct radl.version-string
import .wgpu

PECO-VERSION := (git-version)
run-stage;

struct PecoConfig
    window :
        struct PecoWindowConfig
            title : String
            width : i64
            height : i64
            resizable : bool
            fullscreen : bool
    renderer :
        struct PecoRendererConfig
            presentation-model : wgpu.PresentMode

struct PecoState
    config : PecoConfig

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

inline get-version ()
    PECO-VERSION

do
    let PecoConfig
    let get-version state-accessor
    local-scope;
