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
		ClassVisitor cv = new ClassVisitor(ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
				if (!"getData".equals(name)) return mv;
				if (!"(Ljava/awt/datatransfer/DataFlavor;)Ljava/lang/Object;".equals(descriptor)) return mv;
				pendingPatch = false;
				return new MethodVisitor(ASM9, mv) {
					final Label
							start = new Label(),
							end = new Label();

					@Override
					public void visitCode() {
						super.visitCode();
						super.visitVarInsn(ALOAD, 0);
						super.visitInsn(MONITORENTER);
						super.visitLabel(start);
					}

					@Override
					public void visitEnd() {
						super.visitLabel(end);
						super.visitVarInsn(ALOAD, 0);
						super.visitInsn(MONITOREXIT);
						super.visitInsn(ATHROW);
						super.visitTryCatchBlock(start, end, end, null);
						super.visitEnd();
					}

					@Override
					public void visitInsn(int opcode) {
						if (opcode == RETURN) {
							super.visitVarInsn(ALOAD, 0);
							super.visitInsn(MONITOREXIT);
						}
						super.visitInsn(opcode);
					}
				};
			}
		};
		cr.accept(cv, 0);
	}
}
