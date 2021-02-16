package tests
import com.machiav3lli.backup.handler.ShellHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("shell command")
internal class ShellHandlerTest {

    @Test
    @DisplayName("white space")
    fun test_fromLsOOutput_handlesWhitespace() {
        val fileInfo = ShellHandler.FileInfo.fromLsOOutput(
            "-rw------- 1 user0_a247 group0_a247 15951095 2021-01-19 01:03:29.000000000 +0100 Aurora Store-3.2.8.apk",
            "/data/data/org.fdroid.fdroid/files"
        )
        assertEquals(
            "Aurora Store-3.2.8.apk",
            fileInfo.filePath
        )
        assertEquals(
            "/data/data/org.fdroid.fdroid/files/Aurora Store-3.2.8.apk",
            fileInfo.absolutePath
        )
        assertEquals(
            15951095,
            fileInfo.fileSize
        )
        assertEquals(
            ShellHandler.FileInfo.FileType.REGULAR_FILE,
            fileInfo.fileType
        )
    }

    @Test
    @DisplayName("multiple white space")
    fun test_fromLsOOutput_handlesMultiWhitespace() {
        val fileInfo = ShellHandler.FileInfo.fromLsOOutput(
            "-rw------- 1 user0_a247 group0_a247 15951095 2021-01-19 01:03:29.000000000 +0100 111   333.file",
            "/data/data/org.fdroid.fdroid/files"
        )
        assertEquals(
            "111   333.file",
            fileInfo.filePath
        )
        assertEquals(
            "/data/data/org.fdroid.fdroid/files/111   333.file",
            fileInfo.absolutePath
        )
        assertEquals(
            15951095,
            fileInfo.fileSize
        )
        assertEquals(
            "user0_a247",
            fileInfo.owner
        )
        assertEquals(
            "group0_a247",
            fileInfo.group
        )
        assertEquals(
            1611014609000,
            fileInfo.fileModTime.time
        )
        assertEquals(
            0b0_110_000_000,
            fileInfo.fileMode
        )
        assertEquals(
            ShellHandler.FileInfo.FileType.REGULAR_FILE,
            fileInfo.fileType
        )
    }

    @Test
    @DisplayName("special characters")
    fun test_fromLsOOutput_handlesSpecialChars() {
        val fileInfo = ShellHandler.FileInfo.fromLsOOutput(
            """-rw------- 1 user0_a247 group0_a247 15951095 2021-01-19 01:03:29.000000000 +0100 My|#$%^&*[](){}'"`:;?<~>,.file""",
            "/data/data/org.fdroid.fdroid/files"
        )
        assertEquals(
            "My|#\$%^&*[](){}'\"`:;?<~>,.file",
            fileInfo.filePath
        )
        assertEquals(
            "/data/data/org.fdroid.fdroid/files/My|#\$%^&*[](){}'\"`:;?<~>,.file",
            fileInfo.absolutePath
        )
        assertEquals(
            15951095,
            fileInfo.fileSize
        )
        assertEquals(
            ShellHandler.FileInfo.FileType.REGULAR_FILE,
            fileInfo.fileType
        )
    }

    @Test
    @DisplayName("quote()")
    fun test_quote() {
        assertEquals(
            """${'"'}My\\\|\$\&\"'`\[]\(){}   =:;?<~>-+!%^#*,.file${'"'}""",
            ShellHandler.quote("""My\|$&"'`[](){}   =:;?<~>-+!%^#*,.file""")
        )
    }

    /* seems to be sandboxed and cannot do such things

    data class ShellResult(
        var code : Int = -1,
        var out : MutableList<String> = mutableListOf(),
        var err : MutableList<String> = mutableListOf(),
        var e : Throwable? = null)

    private fun runShell(commands: Array<String>): ShellResult {

        val result = ShellResult()

        try {
            val rt = Runtime.getRuntime()

            println("----- ${commands.joinToString(" ; ")}")

            val proc = rt.exec(commands)

            println("proc: $proc")

            var s: String
            val stdInput = BufferedReader(InputStreamReader(proc.inputStream))
            while (stdInput.readLine().also { s = it } != null) {
                result.out.add(s)
            }
            val stdError = BufferedReader(InputStreamReader(proc.errorStream))
            while (stdError.readLine().also { s = it } != null) {
                result.err.add(s)
            }

            result.code = proc.waitFor()

            println("CODE: ${result.code}")
            println("OUT:")
            println(result.out.joinToString("\n"))
            println("ERR:")
            println(result.err.joinToString("\n"))
        } catch(e: Throwable) {
            println("ERROR: ${e.message}")
            //println(LogUtils.message(e))
        }
        return result
    }

    private fun runShell(command: String): ShellResult { return runShell(arrayOf(command)) }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("quote() in shell")
    fun test_quote_shell() {
        //println("HELLO WORLD")
        val ext = ".nonExistentExt"
        //val filename = """My\|$&"'`[](){}   =:;?<~>-+!%^#*,""" + ext
        //val filename = """test.file"""
        val filename = "test$ext"
        //val filename = "test()$ext"
        val dir = "/tmp/ShellHandlerTest$ext"
        //val dir = "/cache"
        runShell("pwd")
        runShell("ls -l")
        runShell("rm --rec $dir")
        runShell("mkdir -p $dir")
        val command = "touch ${ShellHandler.quote("$dir/$filename")}"
        runShell(command)
        //assertEquals(
        //    0,
        //    runShell(command).code
        //)
        val result = runShell("find $dir/ -type f")
        assertTrue(
            result.code == 0
        )
        assertEquals(
            "$dir/$filename",
            result.out.get(0)
        )
        runShell("rm --rec $dir")
    }
    
    */
}