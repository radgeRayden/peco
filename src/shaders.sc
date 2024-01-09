fn default-vert ()
    using import glsl
    using import glm

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
    using import glsl
    using import glm

    in vcolor : vec4
        location = 0
    out fcolor : vec4
        location = 0
    fcolor = vcolor

compile :=
    inline (f target)
        using import compiler.target.SPIR-V radl.IO.FileStream String
        name :=
            static-eval
                sc_template_get_name
                    sc_closure_get_template f

        try
            fs := FileStream (.. "shaders/" (static-eval (name as string)) ".spv") FileMode.Write
            'write fs
                String
                    static-compile-spirv SPV_ENV_VULKAN_1_1_SPIRV_1_4 target (static-typify f)
        else ()

compile default-vert 'vertex
compile default-frag 'fragment

local-scope;
