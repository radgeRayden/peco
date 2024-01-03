using import Buffer print radl.strfmt String struct
import .logger .toml .wgpu

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

inline match-string-enum (ET value)
    using import hash radl.String+ switcher print
    tolower := ASCII-tolower

    call
        switcher sw
            va-map
                inline (k)
                    case (static-eval (hash (tolower (k as string))))
                        imply k ET
                collect-enum-fields ET
            default
                report value
                raise;
        hash (tolower value)

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
            dst = 'from-rawstring String f.u.s
    default
        static-if (T < CEnum)
            f := toml.table_string table fname
            if f.ok
                value := 'from-rawstring String f.u.s
                try (dst = (match-string-enum T value))
                else ()
        else
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

        t := toml.table_table result "renderer"
        if (t != null)
            renderer := cfg.renderer
            try-set renderer 'presentation-model t
    cfg
do
    let parse default
    local-scope;
