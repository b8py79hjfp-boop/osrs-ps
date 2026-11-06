# Friends chat

Source: OSRS Wiki "Friends chat" (https://oldschool.runescape.wiki/w/Friends_chat). Packet shapes come from RSProt
242: FRIENDCHAT_JOIN_LEAVE, FRIENDCHAT_KICK, FRIENDCHAT_SETRANK, UPDATE_FRIENDCHAT_CHANNEL_FULL_V2,
UPDATE_FRIENDCHAT_CHANNEL_SINGLEUSER and MESSAGE_FRIENDCHANNEL, plus MESSAGE_PUBLIC chat type 2 for "/" lines.

| # | Rule |
|---|---|
| FC1 | A kicked player cannot rejoin that channel for one hour. A channel holds at most 100 players. |
| FC2 | Ranks: the owner is 7 (gold key). Ranks given by the owner run from 1 (recruit) to 6 (general). Friends of the owner are 0 and everyone else is -1 (guest). Jagex moderators show 127; here that means administrators. Ranks can only be given to players on the owner's friend list, and they persist. |
| FC3 | Only players at or above the kick rank can kick, and only players ranked lower than themselves. |
| FC4 | Lines starting with "/" are sent as public chat type 2 and go to every member, across worlds, with the sender's crown. Muted players can't talk. |
| FC5 | Joining leaves the current channel first. Logging out (and therefore hopping) leaves it. The client rejoins by itself after a hop or relog. |

Implementation: membership is hub state (`hub/FriendChats.kt`), so a channel spans worlds. Updates travel on the
social event stream to each member's world (WorldLinks), and ranks are stored in `channel_ranks`.

## OPEN

- **OPEN-FC1, channel settings.** The setup interface isn't implemented yet: channel name, who can enter, talk and
  kick. A channel is named after its owner, anyone can enter and talk, and kicking is owner-only ("Only me").
- **OPEN-FC2, which channels exist.** Real OSRS channels exist only after the owner has set a name. Here every
  account's channel can be joined.
- **OPEN-FC3, message text and type.** The join/leave/kick notices are approximations, sent as plain game messages
  rather than the chat-channel message type.
