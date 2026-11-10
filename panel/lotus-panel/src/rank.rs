//! Game ranks as the hub stores them (docs/features/commands.md): player < moderator < administrator < owner <
//! developer. The panel never decides a rank; it only reads the hub's.

use std::fmt;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub enum Rank {
    Player,
    Moderator,
    Administrator,
    Owner,
    Developer,
}

impl Rank {
    pub const ALL: [Self; 5] = [
        Self::Player,
        Self::Moderator,
        Self::Administrator,
        Self::Owner,
        Self::Developer,
    ];

    /// The hub's stored value (`tp.util.Rank.stored`).
    pub fn parse(stored: &str) -> Option<Self> {
        match stored.to_ascii_uppercase().as_str() {
            "PLAYER" => Some(Self::Player),
            "MODERATOR" => Some(Self::Moderator),
            "ADMIN" | "ADMINISTRATOR" => Some(Self::Administrator),
            "OWNER" => Some(Self::Owner),
            "DEVELOPER" => Some(Self::Developer),
            _ => None,
        }
    }

    pub const fn stored(self) -> &'static str {
        match self {
            Self::Player => "PLAYER",
            Self::Moderator => "MODERATOR",
            Self::Administrator => "ADMIN",
            Self::Owner => "OWNER",
            Self::Developer => "DEVELOPER",
        }
    }

    pub const fn label(self) -> &'static str {
        match self {
            Self::Player => "Player",
            Self::Moderator => "Moderator",
            Self::Administrator => "Administrator",
            Self::Owner => "Owner",
            Self::Developer => "Developer",
        }
    }

    /// Moderator and above may use the panel (brief §2).
    pub fn is_staff(self) -> bool {
        self >= Self::Moderator
    }
}

impl fmt::Display for Rank {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.label())
    }
}
