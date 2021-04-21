import java.io.File

interface Strategy {
    fun getData(path: File): FictionBook?
}