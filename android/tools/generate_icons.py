#!/usr/bin/env python3
"""Generiše TerminIcons.kt iz Material Symbols SVG-ova (npm paket @material-symbols/svg-400).

Upotreba:
  npm pack @material-symbols/svg-400 && tar xzf material-symbols-svg-400-*.tgz
  python3 tools/generate_icons.py package/
Ikone su pod Apache 2.0 licencom (assets/licenses/material-symbols.txt).
"""
import re
import sys
from pathlib import Path

# naziv u kodu -> naziv simbola (sufiks -fill = popunjena varijanta)
ICONS = {
    "Home": "home", "HomeFilled": "home-fill",
    "Search": "search",
    "CalendarMonth": "calendar_month", "CalendarMonthFilled": "calendar_month-fill",
    "Person": "person", "PersonFilled": "person-fill",
    "Event": "event", "EventFilled": "event-fill",
    "Storefront": "storefront", "StorefrontFilled": "storefront-fill",
    "ContentCut": "content_cut", "Air": "air", "Palette": "palette", "Brush": "brush",
    "Face": "face", "Spa": "spa",
    "Favorite": "favorite", "FavoriteFilled": "favorite-fill",
    "Star": "star", "StarFilled": "star-fill", "StarHalf": "star_half-fill",
    "LocationOn": "location_on", "NearMe": "near_me", "Map": "map", "ListView": "list",
    "Tune": "tune", "Close": "close", "ArrowBack": "arrow_back", "ArrowForward": "arrow_forward",
    "ChevronRight": "chevron_right", "ChevronLeft": "chevron_left", "ExpandMore": "keyboard_arrow_down",
    "ExpandLess": "keyboard_arrow_up", "Call": "call", "Directions": "directions", "Share": "share",
    "Schedule": "schedule", "Notifications": "notifications", "Settings": "settings",
    "Logout": "logout", "Delete": "delete", "Edit": "edit", "Add": "add", "Remove": "remove",
    "Check": "check", "CheckCircle": "check_circle", "Error": "error", "Info": "info",
    "Warning": "warning", "WifiOff": "wifi_off", "Refresh": "refresh", "PhotoCamera": "photo_camera",
    "Image": "image", "PhotoLibrary": "photo_library", "Language": "language", "DarkMode": "dark_mode",
    "LightMode": "light_mode", "Contrast": "contrast", "Lock": "lock", "EventAvailable": "event_available",
    "EventBusy": "event_busy", "Today": "today", "Timer": "timer", "PersonAdd": "person_add",
    "Group": "group", "BarChart": "bar_chart", "Link": "link", "Sync": "sync", "Block": "block",
    "BeachAccess": "beach_access", "MoreVert": "more_vert", "Help": "help", "Upload": "upload",
    "FilterList": "filter_list", "History": "history", "Payments": "payments", "Alarm": "alarm",
    "Mail": "mail", "AccountCircle": "account_circle", "Inbox": "inbox", "Reviews": "reviews",
    "Policy": "policy", "OpenInNew": "open_in_new", "Smartphone": "mobile", "Sms": "sms",
    "DragHandle": "drag_handle", "ArrowUpward": "arrow_upward", "ArrowDownward": "arrow_downward",
    "HourglassTop": "hourglass_top", "PinDrop": "pin_drop", "MyLocation": "my_location",
    "ViewDay": "view_day", "ViewWeek": "view_week", "TaskAlt": "task_alt", "PersonOff": "person_off",
    "Extension": "extension", "AutoAwesome": "stars", "Sell": "sell", "Notes": "notes",
    "Description": "description", "Undo": "undo", "Visibility": "visibility",
    "VisibilityOff": "visibility_off", "Badge": "badge", "AddCircle": "add_circle",
    "NotificationsOff": "notifications_off", "Celebration": "celebration", "Bolt": "bolt",
}

STYLES = ("outlined", "rounded", "sharp")


def path_data(svg: str) -> str:
    paths = re.findall(r'<path[^>]* d="([^"]+)"', svg)
    if not paths:
        raise ValueError("no path")
    return " ".join(paths)


def main(root: Path, out: Path) -> None:
    lines = [
        "// Generisano skriptom android/tools/generate_icons.py iz Material Symbols (Apache 2.0).",
        "// Ne menjati ručno.",
        "package rs.termin.app.core.designsystem.icon",
        "",
        "/** Material Symbols putanje (viewBox 0 -960 960 960) za tri stila: Outlined, Rounded, Sharp. */",
        "@Suppress(\"MaxLineLength\")",
        "enum class TerminIcon(",
        "    internal val outlined: String,",
        "    internal val rounded: String,",
        "    internal val sharp: String,",
        ") {",
    ]
    for name, symbol in ICONS.items():
        data = [path_data((root / style / f"{symbol}.svg").read_text()) for style in STYLES]
        lines.append(f'    {name}("{data[0]}", "{data[1]}", "{data[2]}"),')
    lines.append("}")
    out.write_text("\n".join(lines) + "\n")
    print(f"{len(ICONS)} icons -> {out}")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(__file__).resolve().parent.parent / "app/src/main/kotlin/rs/termin/app/core/designsystem/icon/TerminIcon.kt")
