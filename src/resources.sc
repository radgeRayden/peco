using import .common enum radl.IO.FileStream
from (import .wgpu) let chained@

resources := state-accessor 'resources

enum ShaderStage plain
    Vertex
    Fragment
    Compute

enum ShaderLanguage plain
    SPIRV
    GLSL
    WGSL

inline try-from-cache (resource-map path loadf)
    elements mapping := resource-map.elements, resource-map.mapping
    try
        'get mapping path
    else
        try
            'read-all-bytes (FileStream path FileMode.Read)
        then (data)
            resource := loadf data
            id := 'add elements resource
            'set mapping path (copy id)
            id
        else
            raise;

fn load-shader (path)
    try-from-cache resources.shaders path
        inline (code)
            ctx := state-accessor 'renderer

            ptr size := 'data code
            wgpu.DeviceCreateShaderModule ctx.device
                chained@ 'ShaderModuleSPIRVDescriptor
                    codeSize = (size // 4) as u32
                    code = (dupe (ptr as (@ u32)))

fn get-shader (id)
    'get resources.shaders.elements id

do
    let load-shader get-shader ShaderStage ShaderLanguage
    local-scope;
