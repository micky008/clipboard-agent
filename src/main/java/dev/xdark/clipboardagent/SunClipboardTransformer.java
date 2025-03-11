package dev.xdark.clipboardagent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

final class SunClipboardTransformer extends AbstractTransformer {
	static final String CLIPBOARD_CLASS = "sun.awt.datatransfer.SunClipboard";

	@Override
	String className() {
		return CLIPBOARD_CLASS;
	}

	@Override
	void apply(ClassReader cr, ClassWriter cw) {
		int unlocked = getMaxLocalsFor(cr, "getData", "(Ljava/awt/datatransfer/DataFlavor;)Ljava/lang/Object;");
		ClassVisitor cv = new ClassVisitor(ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
				if (!"getData".equals(name)) return mv;
				if (!"(Ljava/awt/datatransfer/DataFlavor;)Ljava/lang/Object;".equals(descriptor)) return mv;
				pendingPatch = false;
				// access |= ACC_SYNCHRONIZED;
				// Class redefinition does not support change of access modifiers,
				// so it must be done the hard way.
				MonitorLocker monitorLocker = new MonitorLocker(unlocked, mv);
				return new MethodVisitor(ASM9, mv) {
					final Label
							start = new Label(),
							end = new Label();

					@Override
					public void visitCode() {
						super.visitCode();
						monitorLocker.init();
						super.visitLabel(start);
					}

					@Override
					public void visitEnd() {
						// Handle exception exit path
						super.visitLabel(end);
						monitorLocker.unlock();
						super.visitInsn(ATHROW);
						super.visitTryCatchBlock(start, end, end, null);
						super.visitEnd();
					}

					@Override
					public void visitInsn(int opcode) {
						if (opcode == MONITORENTER) {
							// Already patched
							throw new IllegalStateException("getData contains monitorenter bytecode");
						}
						if (opcode == ARETURN) {
							// Normal exit path
							monitorLocker.unlock();
						}
						super.visitInsn(opcode);
					}
				};
			}
		};
		cr.accept(cv, 0);
	}
}
