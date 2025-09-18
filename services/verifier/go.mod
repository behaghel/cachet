module github.com/behaghel/cachet/services/verifier

go 1.22

require (
	github.com/behaghel/cachet/services/common v0.0.0
	github.com/go-chi/chi/v5 v5.0.12
	github.com/rs/zerolog v1.32.0
	github.com/stretchr/testify v1.9.0
)

require (
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/mattn/go-colorable v0.1.13 // indirect
	github.com/mattn/go-isatty v0.0.19 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	golang.org/x/sys v0.12.0 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
)

replace github.com/behaghel/cachet/services/common => ../common
