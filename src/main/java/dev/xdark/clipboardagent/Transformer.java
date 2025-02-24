package dev.xdark.clipboardagent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.PrintStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.invoke.LambdaMetafactory;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.*;

final class Transformer implements ClassFileTransformer {
	boolean pendingPatch = true;

	@Override
	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		if (!ClipboardAgent.CLIPBOARD_CLASS_INTERNAL_NAME.equals(className)) return null;
		pendingPatch = false;
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected ClassLoader getClassLoader() {
				return null;
			}
		};
		ClassVisitor cv = new ClassVisitor(ASM9, cw) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
				if ((access & (ACC_STATIC | ACC_NATIVE)) != 0) return mv;
				if (!"handleContentsChanged".equals(name)) return mv;
				if (!"()V".equals(descriptor)) return mv;
				return new MethodVisitor(ASM9, mv) {
					Label start;
					Label end;

					@Override
					public void visitCode() {
						super.visitCode();
						Label skip = new Label();
						// if (AppContent.getAppContext() == null) { goto skip; }
						super.visitMethodInsn(INVOKESTATIC, "sun/awt/AppContext", "getAppContext", "()Lsun/awt/AppContext;", false);
						super.visitJumpInsn(IFNULL, skip);
						// EventQueue.invokeLater(this::handleContentsChanged);
						// return;
						super.visitVarInsn(ALOAD, 0);
						super.visitInvokeDynamicInsn(
								"run",
								String.format("(L%s;)Ljava/lang/Runnable;", ClipboardAgent.CLIPBOARD_CLASS_INTERNAL_NAME),
								new Handle(
										H_INVOKESTATIC,
										Type.getInternalName(LambdaMetafactory.class),
										"metafactory",
										"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
										false
								),
								Type.getMethodType(Type.VOID_TYPE),
								new Handle(
										H_INVOKEVIRTUAL,
										ClipboardAgent.CLIPBOARD_CLASS_INTERNAL_NAME,
										"handleContentsChanged",
										"()V",
										false
								),
								Type.getMethodType(Type.VOID_TYPE)
						);
						super.visitMethodInsn(INVOKESTATIC, "java/awt/EventQueue", "invokeLater", "(Ljava/lang/Runnable;)V", false);
						super.visitInsn(RETURN);
						super.visitLabel(skip);
						// synchronized(this) {
						super.visitVarInsn(ALOAD, 0);
						super.visitInsn(MONITORENTER);
						super.visitLabel(start = new Label());
					}

					@Override
					public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
						monitor_exit:
						{
							if (opcode != INVOKEVIRTUAL) break monitor_exit;
							if (!("sun/awt/SunClipboard".equals(owner) || ClipboardAgent.CLIPBOARD_CLASS_INTERNAL_NAME.equals(owner)))
								break monitor_exit;
							if (!"checkChange".equals(name)) break monitor_exit;
							if (!"([J)V".equals(descriptor)) break monitor_exit;
							// } END synchronized
							super.visitLabel(end = new Label());
						}
						super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
					}

					@Override
					public void visitEnd() {
						Label end = this.end;
						if (end == null) {
							throw new IllegalStateException("Did not see call to checkChange");
						}
						// Handle exception exit path
						Label handler = new Label();
						super.visitLabel(handler);
						super.visitVarInsn(ALOAD, 0);
						super.visitInsn(MONITOREXIT);
						super.visitInsn(ATHROW);
						super.visitTryCatchBlock(start, end, handler, null);
						super.visitEnd();
					}

					@Override
					public void visitInsn(int opcode) {
						if (opcode == MONITORENTER) {
							// Already patched
							throw new IllegalStateException("handleContentsChanged contains monitorenter bytecode");
						}
						// Normal exit path
						if (opcode == RETURN) {
							super.visitVarInsn(ALOAD, 0);
							super.visitInsn(MONITOREXIT);
						}
						super.visitInsn(opcode);
					}
				};
			}
		};
		try {
			cr.accept(cv, 0);
		} catch (Exception ex) {
			PrintStream err = System.err;
			synchronized (err) {
				err.printf("Failed to apply the patch to %s%n", ClipboardAgent.CLIPBOARD_CLASS);
				ex.printStackTrace(err);
			}
		}
		return cw.toByteArray();
	}
}
