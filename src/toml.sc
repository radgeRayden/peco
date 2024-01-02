header := include "toml-c.h"

do
    using header.extern filter "^toml_(.+)$"
    using header.typedef filter "^toml_(.+?)_t$"
    local-scope;
