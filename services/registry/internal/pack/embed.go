package pack

import (
	"embed"
	"io/fs"
)

// packsFS embeds the Trust Pack definition JSON files.
//
//go:embed packs/*.json
var packsFS embed.FS

// EmbeddedPacksFS returns a filesystem rooted at the packs/ subdirectory.
func EmbeddedPacksFS() fs.FS {
	sub, err := fs.Sub(packsFS, "packs")
	if err != nil {
		panic("embedded packs directory missing: " + err.Error())
	}
	return sub
}
