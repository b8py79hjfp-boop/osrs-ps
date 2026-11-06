package tp.client.gamepatch

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

/**
 * Removes Jagex's "use the Jagex Launcher" login-screen prompt (DECISIONS D-028). Clicking "Existing User" puts the
 * login screen into state [NAG] ("The move is almost here"); with no launcher in our release, that screen only leads
 * to Jagex's site. No jav_config param turns it off (params 3, 8, 16 and 30 were tried: 30=false shows a stricter
 * screen with no Login button at all).
 *
 * The state is the client's `loginIndex`. It is found without obfuscated names, through RuneLite's stable
 * `client.getLoginIndex()`: its first GETSTATIC is the field, and its LDC int is the decoding multiplier (Jagex
 * stores ints multiplied by an odd constant). After every write to that field, a call to [HELPER].`fix` turns state
 * [NAG] into the plain login form, state [LOGIN_FORM]. The game's own methods gain no branches, so their stack-map
 * frames stay valid; the helper is a new class added to the jar. The number of instrumented writes must equal the
 * pinned `loginNagSites` in patches.json.
 */
object LoginNagPatch {
    const val RULE = "login-launcher-nag"
    const val HELPER = "tp/gamepatch/LoginNag"
    const val HELPER_FILE = "$HELPER.class"

    /** loginIndex values: 38 = the launcher prompt, 2 = the username/password form (observed in the 242 client). */
    const val NAG = 38
    const val LOGIN_FORM = 2

    class Target(val owner: String, val name: String, val decoder: Int) {
        /** Jagex encodes with the modular inverse of the decoder (mod 2^32). */
        val encoder: Int = BigInteger.valueOf(decoder.toLong() and MASK).modInverse(BigInteger.ONE.shiftLeft(Int.SIZE_BITS)).toInt()
    }

    /** Finds the loginIndex field and decoder through `client.getLoginIndex()`; null if the client has no such method. */
    fun find(jar: Path): Target? {
        ZipInputStream(Files.newInputStream(jar)).use { z ->
            while (true) {
                val e = z.nextEntry ?: return null
                if (e.name != "client.class") continue
                var field: Pair<String, String>? = null
                var decoder: Int? = null
                val reader =
                    object : ClassVisitor(Opcodes.ASM9) {
                        override fun visitMethod(
                            access: Int,
                            name: String?,
                            descriptor: String?,
                            signature: String?,
                            exceptions: Array<out String>?,
                        ): MethodVisitor? {
                            if (name != "getLoginIndex" || descriptor != "()I") return null
                            return object : MethodVisitor(Opcodes.ASM9) {
                                override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                                    if (opcode == Opcodes.GETSTATIC && field == null) field = owner to name
                                }

                                override fun visitLdcInsn(value: Any?) {
                                    if (value is Int && decoder == null) decoder = value
                                }
                            }
                        }
                    }
                ClassReader(z.readAllBytes()).accept(reader, ClassReader.SKIP_FRAMES)
                val f = field ?: return null
                return Target(f.first, f.second, decoder ?: return null)
            }
        }
    }

    /** Wraps a method visitor: after each PUTSTATIC of the target field, re-store `fix(field)`. */
    fun instrument(mv: MethodVisitor, t: Target, onSite: () -> Unit): MethodVisitor =
        object : MethodVisitor(Opcodes.ASM9, mv) {
            override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
                super.visitFieldInsn(opcode, owner, name, descriptor)
                val isTarget = owner == t.owner && name == t.name && descriptor == "I"
                if (opcode == Opcodes.PUTSTATIC && isTarget) {
                    onSite()
                    super.visitFieldInsn(Opcodes.GETSTATIC, owner, name, "I")
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "fix", "(I)I", false)
                    super.visitFieldInsn(Opcodes.PUTSTATIC, owner, name, "I")
                }
            }
        }

    /**
     * `public final class tp.gamepatch.LoginNag { public static int fix(int encoded) }`: returns
     * `encode(LOGIN_FORM)` when `decode(encoded) == NAG`, else `encoded` unchanged.
     */
    fun helperClass(t: Target): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER, HELPER, null, "java/lang/Object", null)
        val mv = cw.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "fix", "(I)I", null, null)
        mv.visitCode()
        val keep = org.objectweb.asm.Label()
        mv.visitVarInsn(Opcodes.ILOAD, 0)
        mv.visitLdcInsn(t.decoder)
        mv.visitInsn(Opcodes.IMUL)
        mv.visitLdcInsn(NAG)
        mv.visitJumpInsn(Opcodes.IF_ICMPNE, keep)
        mv.visitLdcInsn(LOGIN_FORM * t.encoder)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitLabel(keep)
        mv.visitVarInsn(Opcodes.ILOAD, 0)
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitMaxs(0, 0)
        mv.visitEnd()
        cw.visitEnd()
        return cw.toByteArray()
    }

    /** A class visitor that instruments every method of one class. */
    fun visitor(next: ClassVisitor, t: Target, onSite: () -> Unit): ClassVisitor =
        object : ClassVisitor(Opcodes.ASM9, next) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor = instrument(super.visitMethod(access, name, descriptor, signature, exceptions), t, onSite)
        }

    private const val MASK = 0xFFFFFFFFL
}
