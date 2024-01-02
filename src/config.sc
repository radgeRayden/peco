using import Buffer print radl.strfmt String struct
import .logger .toml .wgpu

struct PecoConfig plain
    window :
        struct PecoWindowConfig plain
            width : i64
            height : i64
            resizable : bool
            fullscreen : bool
    renderer :
        struct PecoRendererConfig plain
            presentation-model : wgpu.PresentMode

fn default ()
    (PecoConfig)

inline try-set (cfg field table)
    dst := getattr cfg field
    T := typeof dst
    fname := static-eval (field as string)

    static-match T
    case i64
        f := toml.table_int table fname
        if f.ok
            dst = f.u.i
    case f64
        f := toml.table_double table fname
        if f.ok
            dst = f.u.d
    case bool
        f := toml.table_bool table fname
        if f.ok
            dst = f.u.b
    case String
        f := toml.table_string table fname
        if f.ok
            dst = 'from-rawstring String f.u.s f.u.sl
    default
        static-error "unsupported configuration type"

fn parse (str)
    err := heapbuffer char 256
    err-ptr err-size := 'data err
    result := toml.parse str err-ptr (err-size as i32)
    defer toml.free result

    local cfg : PecoConfig
    if (result == null)
        logger.write-warning f"While parsing config file: ${String err-ptr err-size}"
    else
        t := toml.table_table result "window"
        if (t != null)
            window := cfg.window
            try-set window 'resizable t
            try-set window 'fullscreen t
            try-set window 'width t
            try-set window 'height t
    cfg
do
    let parse default
    local-scope;
