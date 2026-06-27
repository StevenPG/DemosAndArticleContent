# Build the mathlib shared library for the current platform.
#
#   make            -> build the shared library into ./build
#   make clean      -> remove ./build
#
# Output is a platform-appropriate shared object:
#   macOS  -> build/libmathlib.dylib
#   Linux  -> build/libmathlib.so

CC      ?= cc
CFLAGS  ?= -O2 -Wall -Wextra -fPIC -Iinclude
BUILD   := build
SRC     := src/mathlib.c

UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
	LIB     := $(BUILD)/libmathlib.dylib
	LDFLAGS := -dynamiclib -install_name @rpath/libmathlib.dylib
else
	LIB     := $(BUILD)/libmathlib.so
	LDFLAGS := -shared
endif

.PHONY: all clean print-lib

all: $(LIB)

$(LIB): $(SRC) include/mathlib.h | $(BUILD)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(SRC)
	@echo "Built $@"

$(BUILD):
	mkdir -p $(BUILD)

# Used by the language demos to discover the freshly built library path.
print-lib:
	@echo $(LIB)

clean:
	rm -rf $(BUILD)
