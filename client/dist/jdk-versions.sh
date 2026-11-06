# Pinned JDKs the jlink runtime is built from (sourced by package.sh; mirrors RuneLite launcher 2.6.7 `.jdk-versions.sh`,
# https://github.com/runelite/launcher/blob/2.6.7/.jdk-versions.sh, D-028).
#
# RuneLite's launcher bundles Java 11 (11.0.19) on every platform except macOS arm64, which gets Java 17 (17.0.7): the
# launcher and the client run on this runtime, so the major version is the one RuneLite's builds are tested on.
# RuneLite's table lists JREs; jlink needs the full JDK (jmods), so these are the matching JDK archives.
# Checksums: .sha256.txt files published next to each Temurin release asset; windows-arm64 is Microsoft's build
# (Temurin has no Windows arm64 JDK 11), checksum taken from RuneLite's table (same file, verified when pinned).
#
# jdk_select <os> <arch>  with os in linux|windows|macos, arch in x64|arm64; sets
#   JDK_VERSION, JDK_URL, JDK_SHA256, JDK_ARCHIVE (file name to store the download as)
jdk_select() {
  local temurin11="https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.19%2B7"
  local temurin17="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.7%2B7"
  case "$1-$2" in
    linux-x64)
      JDK_VERSION=11.0.19+7
      JDK_URL="$temurin11/OpenJDK11U-jdk_x64_linux_hotspot_11.0.19_7.tar.gz"
      JDK_SHA256=5f19fb28aea3e28fcc402b73ce72f62b602992d48769502effe81c52ca39a581 ;;
    linux-arm64)
      JDK_VERSION=11.0.19+7
      JDK_URL="$temurin11/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.19_7.tar.gz"
      JDK_SHA256=0c7763a19b4af4ef5fbae831781b5184e988d6f131d264482399eeaf51b6e254 ;;
    macos-x64)
      JDK_VERSION=11.0.19+7
      JDK_URL="$temurin11/OpenJDK11U-jdk_x64_mac_hotspot_11.0.19_7.tar.gz"
      JDK_SHA256=fc34c4f0e590071dcd65a0f93540913466ccac3aa8caa984826713b67afb696d ;;
    macos-arm64)
      JDK_VERSION=17.0.7+7
      JDK_URL="$temurin17/OpenJDK17U-jdk_aarch64_mac_hotspot_17.0.7_7.tar.gz"
      JDK_SHA256=1d6aeb55b47341e8ec33cc1644d58b88dfdcce17aa003a858baa7460550e6ff9 ;;
    windows-x64)
      JDK_VERSION=11.0.19+7
      JDK_URL="$temurin11/OpenJDK11U-jdk_x64_windows_hotspot_11.0.19_7.zip"
      JDK_SHA256=4cf9a6eab52c405f470c887a2f9277a93f9d0ae681abb3dea8e26fff2051c4b4 ;;
    windows-arm64)
      JDK_VERSION=11.0.19+7
      JDK_URL="https://aka.ms/download-jdk/microsoft-jdk-11.0.19-windows-aarch64.zip"
      JDK_SHA256=2f321bfe2cd2a4759b061f37c82d57d8944e0983bda84d6f9a4d790dd21ccb94 ;;
    *) echo "jdk_select: unsupported platform '$1-$2'" >&2; return 1 ;;
  esac
  JDK_ARCHIVE="jdk-$1-$2.${JDK_URL##*.}"
  case "$JDK_URL" in *.tar.gz) JDK_ARCHIVE="jdk-$1-$2.tar.gz" ;; esac
}
