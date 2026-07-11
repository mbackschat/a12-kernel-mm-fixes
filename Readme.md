<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://www.mgm-tp.com/global-content/cd/logos/a12/app-icons/dark/A12-Dark.svg" />
  <img src="https://www.mgm-tp.com/global-content/cd/logos/a12/app-icons/light/A12-Light.svg" height="200" alt="A12 logo" />
</picture>

# kernel_mm

Metamodels for various models that are relevant for kernel, in A12 DocumentModel format.

Refer to https://geta12.com/#/docs to get started with A12 development

---

## License

Parts of the A12 platform are made available under a **dual license**.  
Please check the [LICENSE](./LICENSE) file for details.

---

## Getting Started

### How to Use It

#### Artifact Coordinates

These artifacts are typically consumed as transitive dependencies via other A12 components, but those artifacts that don't carry an `internal` infix can also be used by projects directly. For reference, the artifact coordinates are:

**Maven** (`groupId: com.mgmtp.a12.kernel`, artifactIds roughly of the form):
- `mmvalidators[-<visibility>]-<modelName>-<modelVersion>-<variant>`
- `mmdmjsons[-<visibility>]-<modelName>-<modelVersion>-<variant>`
- `mmtypings[-<visibility>]-<modelName>-<modelVersion>[-<variant>]`

**npm** (package names roughly of the form):
- `@com.mgmtp.a12.kernel/mmdmjsons[-<visibility>]-<modelName>-<modelVersion>-<variant>`

Where `<visibility>` is an infix such as `a12internal` or `extmpinternal`, absent for public artifacts.

---

### How to Build and Run

#### Prerequisites

- OpenJDK 21
- Node.js 22
- Gradle 9

#### How to Build

Artifacts are built and published via Gradle aggregate tasks. The task names follow the pattern:

```
gradle aggregate<modelName>-<modelVersion>_<task> --parallel   # single model/version
gradle aggregateAllDVs_<task> --parallel                # all models/versions
```

Common tasks:
- `build` — compile and assemble all artifacts
- `publishToMavenLocal` — publish to the local Maven repository

#### How to Test

The build config of this repo can be tested as follows:

```
gradle :test-mmdmjsons:test :test-mmtypings:test :test-mmvalidators:test
```

---

### Bootstrapping initial Validator Code

The code generation for metamodel validator code uses kernel code generation (from the previous kernel release, typically).
Since kernel code generation uses metamodel validators by default, a bootstrapping problem arises.
The environment variable `A12_KERNEL_BOOTSTRAP_VALIDATORS_DISABLE_MM_VALIDATION` can be used to solve this by disabling the use of kernel metamodel
validators during generation of validator code. It should only be used with input DocumentModels that are known to be valid, such as those
that are stored in the `kernel_mm` repository, and only for initial bootstrapping.
The resulting validator code will only have the kernel runtime and `kernel-core-runtime-api` as runtime dependencies, which can be built
without kernel's codegen subprojects and therefore without a dependency to `kernel_mm`.

---

### Documentation

- Full technical documentation is available at [GetA12.com](https://GetA12.com).  
- The website also provides access to the **A12 Discourse Community Forum**.

---

**The mgm A12 Team**

[mgm technology partners GmbH](https://www.mgm-tp.com) | [Imprint](https://www.mgm-tp.com/imprint.html)
