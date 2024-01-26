using import glm glsl struct

fn default-vert ()
    out vcolor : vec4
        location = 0

    local vertices =
        arrayof vec3
            vec3 0.0 0.5 0.0
            vec3 -0.5 -0.5 0.0
            vec3 0.5 -0.5 0.0

    local colors =
        arrayof vec4
            vec4 1.0 0.0 0.0 1.0
            vec4 0.0 1.0 0.0 1.0
            vec4 0.0 0.0 1.0 1.0

    idx := gl_VertexIndex
    vcolor = (colors @ idx)
    gl_Position = (vec4 (vertices @ idx) 1.0)

fn default-frag ()
    in vcolor : vec4
        location = 0
    out fcolor : vec4
        location = 0
    fcolor = vcolor

fn scaled-output-vert ()
    local vertices =
        arrayof vec2
            vec2 -1.0  1.0 # TL
            vec2 -1.0 -1.0 # BL
            vec2  1.0 -1.0 # BR
            vec2 -1.0  1.0 # TL
            vec2  1.0 -1.0 # BR
            vec2  1.0  1.0 # TR

    local texcoords =
        arrayof vec2
            vec2 0 0
            vec2 0 1
            vec2 1 1
            vec2 0 0
            vec2 1 1
            vec2 1 0

    out vtexcoords : vec2 (location = 0)

    idx := gl_VertexIndex
    vtexcoords = (texcoords @ idx)
    gl_Position = vec4 (vertices @ idx) 0.0 1.0

fn scaled-output-frag ()
    uniform s : sampler (set = 0) (binding = 0)
    uniform t : texture2D (set = 0) (binding = 1)

    in vtexcoords : vec2 (location = 0)
    out fcolor : vec4 (location = 0)

    fcolor = (texture (sampler2D t s) vtexcoords)

compile :=
    inline (f target)
        using import compiler.target.SPIR-V radl.IO.FileStream String
        name :=
            static-eval
                sc_template_get_name
                    sc_closure_get_template f

        try
            fs :=
                FileStream
                    (.. project-dir "/shaders/" (static-eval (name as string)) ".spv")
                    FileMode.Write
            'write fs
                String
                    static-compile-spirv SPV_ENV_VULKAN_1_1_SPIRV_1_4 target (static-typify f)
        else ()

sugar-if main-module?
    compile default-vert 'vertex
    compile default-frag 'fragment
    compile scaled-output-vert 'vertex
    compile scaled-output-frag 'fragment

do
    local-scope;
