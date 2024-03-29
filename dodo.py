import platform
import os
from doit.tools import LongRunning

# DOIT_CONFIG = {'default_tasks': ['build_demos']}
FILTER_ANSI = os.getenv("DOIT_FILTER_ANSI")

def cmd(cmd):
    if platform.system() == "Linux" and FILTER_ANSI:
        return f"bash -c \"{cmd} | ansi2txt\" || {cmd}"
    else:
        return f"bash -c \"{cmd}\""

def task_eo():
    return {
        'actions': [cmd("wget -O eo https://hg.sr.ht/~duangle/majoreo/raw/eo?rev=tip"), cmd("chmod +x eo")],
        'targets': ["eo"],
        'uptodate': [True]
    }

bootstrap = ".eo/installed/bootstrap"
def task_bootstrap():
    return {
        'verbosity': 2,
        'actions': [cmd("./eo init; true"), cmd("./eo install -y bootstrap")],
        'file_dep': ["eo"],
        'targets': [bootstrap]
    }

def task_force_bootstrap():
    return {
        'verbosity': 2,
        'actions': [cmd("rm -rf .eo lib include"), cmd("./eo init"), cmd("./eo update"), cmd("./eo install -y bootstrap")],
        'uptodate': [False],
        'file_dep': ["eo"],
    }

def task_build_shaders():
    return {
        'verbosity': 2,
        'actions': [LongRunning(f"scopes -e -m .src.shaders")],
        'file_dep': [bootstrap],
        'uptodate': [False]
    }

def task_boot():
    return {
        'verbosity': 2,
        'actions': [LongRunning(f"scopes -e -m .src.main")],
        'file_dep': [bootstrap],
        'uptodate': [False]
    }
