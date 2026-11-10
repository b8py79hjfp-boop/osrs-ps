//! Network helpers: CIDR ranges for `allowed_networks`, and the client address behind Caddy.

use std::net::IpAddr;
use std::str::FromStr;

use serde::Deserialize;

/// An address range such as `100.64.0.0/10` or `fd7a:115c:a1e0::/48`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(try_from = "String")]
pub struct Cidr {
    base: IpAddr,
    prefix: u8,
}

impl Cidr {
    pub fn contains(self, ip: IpAddr) -> bool {
        match (self.base, ip) {
            (IpAddr::V4(b), IpAddr::V4(a)) => {
                mask_eq(u128::from(b.to_bits()), u128::from(a.to_bits()), 32, self.prefix)
            }
            (IpAddr::V6(b), IpAddr::V6(a)) => mask_eq(b.to_bits(), a.to_bits(), 128, self.prefix),
            (IpAddr::V6(b), IpAddr::V4(a)) => self.contains_mapped(b, a),
            (IpAddr::V4(_), IpAddr::V6(a)) => a.to_ipv4_mapped().is_some_and(|v4| self.contains(IpAddr::V4(v4))),
        }
    }

    fn contains_mapped(self, base: std::net::Ipv6Addr, ip: std::net::Ipv4Addr) -> bool {
        mask_eq(base.to_bits(), ip.to_ipv6_mapped().to_bits(), 128, self.prefix)
    }
}

fn mask_eq(base: u128, ip: u128, bits: u8, prefix: u8) -> bool {
    if prefix == 0 {
        return true;
    }
    let shift = u32::from(bits - prefix);
    (base >> shift) == (ip >> shift)
}

#[derive(Debug, thiserror::Error)]
#[error("invalid network {0:?}: expected an address with a /prefix, e.g. 100.64.0.0/10")]
pub struct CidrError(String);

impl FromStr for Cidr {
    type Err = CidrError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let bad = || CidrError(s.to_owned());
        let (addr, prefix) = s.split_once('/').ok_or_else(bad)?;
        let base: IpAddr = addr.trim().parse().map_err(|_| bad())?;
        let prefix: u8 = prefix.trim().parse().map_err(|_| bad())?;
        let max = if base.is_ipv4() { 32 } else { 128 };
        if prefix > max {
            return Err(bad());
        }
        Ok(Self { base, prefix })
    }
}

impl TryFrom<String> for Cidr {
    type Error = CidrError;

    fn try_from(value: String) -> Result<Self, Self::Error> {
        value.parse()
    }
}

/// The real client address: Caddy's `X-Forwarded-For` (its last hop) when the connection comes from loopback,
/// otherwise the peer itself. A header from anyone but the local proxy is ignored.
pub fn client_ip(peer: IpAddr, forwarded_for: Option<&str>) -> IpAddr {
    if !peer.is_loopback() {
        return peer;
    }
    forwarded_for
        .and_then(|v| v.rsplit(',').next())
        .and_then(|v| v.trim().parse().ok())
        .unwrap_or(peer)
}
