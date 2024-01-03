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

inline try-set (dst table key)
    T := typeof dst

    inline copy-field (getter setter)
        field := getter table key
        if field.ok
            setter field

    static-match T
    case i64
        copy-field toml.table_int
            (f) -> (dst = f.u.i)
    case f64
        copy-field toml.table_double
            (f) -> (dst = f.u.d)
    case bool
        copy-field toml.table_bool
            (f) -> (dst = f.u.b)
    case String
        copy-field toml.table_string
            (f) -> (dst = ('from-rawstring String f.u.s))
    default
        static-if (T < CEnum)
            copy-field toml.table_string
                inline (f)
                    try (dst = (match-string-enum T ('from-rawstring String f.u.s)))
                    else ()
        else
            static-error "unsupported configuration type"

fn toml->struct (table dst)
    recurse := this-function
    va-map
        inline (fT)
            k T := keyof fT.Type, unqualified fT.Type
            field := getattr dst k

            key := static-eval (k as string)
            static-if (T < CStruct)
                new-table := toml.table_table table key
                if (new-table != null)
                    recurse new-table field
            else
                try-set field table key
        (typeof dst) . __fields__

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

fn parse (str)
    err := heapbuffer char 256
    err-ptr err-size := 'data err
    result := toml.parse str err-ptr (err-size as i32)
    defer toml.free result

    local cfg : PecoConfig
    if (result == null)
        logger.write-warning f"While parsing config file: ${String err-ptr err-size}"
    else
        toml->struct result cfg

    cfg
do
    let parse default
    local-scope;
