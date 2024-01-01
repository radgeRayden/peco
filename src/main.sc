using import radl.strfmt radl.version-string

PECO-VERSION := (git-version)
run-stage;

fn main (argc argv)
    print f"peco ${PECO-VERSION}"
    0

main 0 0
