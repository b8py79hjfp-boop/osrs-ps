# Clans

The 2021 clan system: one ranked clan per player, any number of guest channels (one at a time here), the clan chat
("//") and guest chat ("///"), founding at the Clan Hub. The hub owns all clan state (`hub/.../Clans.kt`, tables
`clans`, `clan_members`, `clan_bans`); world nodes only forward requests and draw what the hub sends
(`server/.../ClanLinks.kt`, adapter `ClanProt.kt`). Content: `content/world/.../Clans.kt`.

Sources: OSRS Wiki "Clan", "Clan Hub", "Clerk (Clan Hub)", "Clan vexillum" (CC BY-NC-SA 3.0); RSProt clan packets;
RuneLite `ClanRank` / `ClanID`.

## Rules

| # | Rule | Source |
|---|---|---|
| CL1 | A clan is founded at the Clan Hub (Grand Exchange south-east corner): talking to the clerk (10759) while clanless opens the creation side panel (interface 705). The founder picks a unique name, then invites four players; when the fourth accepts, the clan exists with the founder as owner (rank 126) and the others as recruits. | Wiki "Clan" (Creating a clan) |
| CL2 | Founders are invited with the panel's Invite button or "Recruit" on a player in the Clan Hub; members whose rank may recruit get "Recruit" in the Clan Hub or while wearing a clan vexillum. The invitee must be online and clanless, and not banned from the clan. | Wiki "Clan", "Clan vexillum" |
| CL3 | The invitee is asked "<name> invites you to join <clan>." with Accept / Decline; accepting adds them as a recruit (or as a founder). A clan has at most 500 members. | Wiki "Clan" |
| CL4 | Leaving drops the clan channel and settings. When the owner leaves, ownership passes to the highest rank, the longest-standing member first; the last member leaving deletes the clan. | Wiki "Clan" (Transferring ownership) |
| CL5 | Administrators (rank 100) and above set ranks of members below their own rank, never to their own rank or above. | RuneLite `ClanRank` |
| CL6 | Guests: anyone not banned may join a clan's channel as a guest from the guest side tab (interface 702) when the clan allows guests; guests have rank -1 and talk with "///". | Wiki "Clan" (Applying), RSProt `MessagePublic` type 4 |
| CL7 | Members at or above the kick rank may kick (or ban) a lower-ranked player from the channel's member menu (CLANCHANNEL_KICKUSER / AFFINEDCLANSETTINGS_ADDBANNED_FROMCHANNEL). A kicked member leaves the clan; a ban also stops guest joins and invites. | RSProt clan packets |
| CL8 | Channel rules: talk rank, kick rank, guests allowed, recruit rank (OPEN-CL3 defaults: talk guest, kick and recruit administrator, guests allowed). | - |
| CL9 | The client draws the clan from CLANCHANNEL_FULL (members online: rank, world) and CLANSETTINGS_FULL (all ranked members with their rune day of joining, the ban list, the ranks), clanType 0 for the own clan and -1 for the guest clan (OPEN-CL4). Both are resent in full on each change, and again when the client asks (CLANCHANNEL_FULL_REQUEST / CLANSETTINGS_FULL_REQUEST; "no clan" if none). Varbits `clan_affinitystatus` (933) / `clan_guest_affinitystatus` (12164) tell the panels the state: 1 no clan, 3 loaded (client script 4288; 0 shows "Connecting..."). The side tab's buttons 1-3 show the clan, guest clan and grouping panels. | RSProt `ClanChannelFull`, `ClanSettingsFull`; RuneLite `ClanID` |
| CL10 | Rune day = days since 27 February 2002 (the client's clan join dates). | RSProt `ClanSettingsFull` |
| CL11 | Chat: public chat type 3 goes to the clan channel, type 4 to the guest channel (MESSAGE_CLANCHANNEL to every online listener); the talk rank and mutes apply. Joining prints "Now talking in <clan>." as a clan system line. | RSProt `MessagePublic`, `MessageClanChannel(System)` |
| CL12 | Presence: the hub resends the channel to every listener when a member logs in, logs out or hops. | - |

## Tick timeline

Clan actions are asynchronous hub calls: the request leaves on the tick of the click; the answer (an error message,
or the new channel state to everyone affected) arrives on a later tick through the social stream.

## OPEN

- **OPEN-CL1:** the rank number of a new recruit (custom rank titles are not modelled; 10 here).
- **OPEN-CL2:** clan name rules (1-12 letters, digits, spaces here).
- **OPEN-CL3:** the default channel rules of a new clan.
- **OPEN-CL4:** the guest clanType value (-1 here; RSProt only says guest ids are negative).
- **OPEN-CL5:** our chat texts for leaving, invitations and refusals (the Wiki gives none).
- **OPEN-CL6:** the player op slot of "Recruit" (6 here) and the Accept Aid check on vexillum recruits.
- **OPEN-CL7:** Clan Hub bounds (x 3172-3190, y 3464-3480 around the Wiki map point 3180, 3474).
- **OPEN-CL8:** not modelled: 25 hours played per founder, one clan per player per day, the 7-day resign countdown,
  90-day inactive owner, applications, recruitment board, Clan Hall, coffer, cloaks/vexillums from the clerk, custom
  rank titles, clan settings interface (706), clan events and broadcasts, the client's clan_joinchat packet.
