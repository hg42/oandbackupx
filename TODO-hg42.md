
### WIP

### next

### bug

* special items missing in custom list
* refresh when changing backup dir (or permissions etc.)

### check

* shell: a && b => su a && su b ?
* selinux attribute?

### mystery

* closed app without exception
    * how is it possible?
    * why is this dependent on old app data (after uninstall+install)?
    * clear storage?

### improve

* base features
    * tar command instead of TarUtils? store archive in cache instead of all the files (probably faster)
    * oabx should restore own settings
    * encrypt/decrypt single/all backups
    * lsof STOP/CONT
    * shell commands via scripts?
    * file watchers
    * special packages paths and size display

* interaction
    * launcher shortcut
    * tasker integration
    * intent interface

* organize 
    * separate apk + data count
    * lock app backup (prevent deleting, exclude from count?)
    * apk pool, no duplicates, simulate hardlinks
        add incremental:
        * backup: add the relative path of the apk to the properties file without using it
        * restore: use the entry to get the apk on restore
        * restore: use a default if the entry is missing
        final step:
        * use a different path (apk-pool) + add the cleanup procedure
        apks could be deleted after each refresh:
            * read the pool contents to a list
            * scan all properties and remove every mentioned apk from the list
            * delete the remaining apks in the list
        It's basically like garbage collection.
        no additional io:
        * the file lists are already read in
        * for this it doesn't matter in which directory the apks are found
        * the number of files is reduced (removing duplicated apks)
        * the properties files are also read in
        just storing a reference into the apk pool and a kind of garbage collector
        (one sweep on the properties in RAM).
        Theroretically, it would also be possible to add the number of references to each apk
        (weird way: store it in file name,
         more traditional: in a single properties file for the apk pool).
        This way you don't even need to read the pool files,
        only delete the apk file if the reference becomes zero.
        The apk name should contain the version, so it does not interfere.

* ui
    * distance of checkboxes in backup list

    * apk/data status
    
        we may arrive at a matrix:
        [ ] [ ] [ ] [ ] Exists in System
        [ ] [ ] [ ] [ ] Should be backed up
        [ ] [ ] [ ] [ ] Count of backups
        or some intelligent way to put this all in one...
    
        may be like this:
        the tag we have now could have these states:
        - not existing in system (blank?)
        - disabled (crossed, touching the icon toggles this)
        - no backup yet (gray or dark icon color or a warning color?)
        - backup ok (normal color)
        no backup could also be symbolized by a circle around the icon (warning, mnemonic an >O<pen task)
    
        * "backup of x is not necessary but it is done".

### SOLVED

* add modification time to tar
* complete file permissions
* app sheet add run
