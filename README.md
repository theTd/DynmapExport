# DynmapExport
A small program to arrange tiles rendered by dynmap to a large image.

Usage:

Option                   Description
------                   -----------
--db-password <String>
--db-url <String>        like jdbc:mysql://localhost/mc
--db-user <String>
--map-id <String>        usually was "flat"
--table-prefix <String>  such as "dynmap_build_"
--threads <Integer>      working threads, should be integer, default to 10,
                           must > 0 (default: 10)
--world <String>         world name
--zoom <Integer>         zoom, should be integer, starting from 0
