using import Buffer .exceptions print radl.IO.FileStream radl.strfmt
    \ .common String struct
import .logger .toml

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

fn try-set (dst table key)
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
            sT := superof T
            field := getattr dst k

            key := static-eval (k as string)
            static-if ((sT == CStruct) or (sT == Struct))
                new-table := toml.table_table table key
                if (new-table != null)
                    recurse new-table field
            else
                try-set field table key
        (typeof dst) . __fields__

fn parse (str cfg)
    err := heapbuffer char 256
    err-ptr err-size := 'data err
    result := toml.parse str err-ptr (err-size as i32)
    defer toml.free result

    if (result == null)
        logger.write-warning f"While parsing config file: ${String err-ptr err-size}"
        raise PecoConfigError.ParsingError
    else
        toml->struct result cfg

fn... init (path : String = "config.toml")
    cfg := (state-accessor) . config
    cfg.window =
        typeinit
            title = f"peco ${(get-version)}"
            width = 1280
            height = 720
            resizable = true
            fullscreen = false

    cfg.renderer =
        typeinit
            presentation-model = 'FifoRelaxed

    try
        fs := FileStream path FileMode.Read
        parse ('read-all-string fs) cfg
    else ()

do
    let init
    local-scope;
