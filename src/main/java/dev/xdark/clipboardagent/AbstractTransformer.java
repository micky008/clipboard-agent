package dev.xdark.clipboardagent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.PrintStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

abstract class AbstractTransformer implements ClassFileTransformer {
	boolean pendingPatch = true;

	abstract String className();

	abstract void apply(ClassReader cr, ClassWriter cw);

	@Override
	public final byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
		String internalName = className().replace('.', '/');
		if (!internalName.equals(className)) return null;
		ClassReader cr = new ClassReader(classfileBuffer);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected ClassLoader getClassLoader() {
				return null;
			}
		};
		try {
			apply(cr, cw);
		} catch (Exception ex) {
			PrintStream err = System.err;
			synchronized (err) {
				err.printf("Failed to apply the patch to %s%n", className());
				ex.printStackTrace(err);
			}
			return null;
		}
		byte[] result = cw.toByteArray();
		return result;
	}

	protected final int getMaxLocalsFor(ClassReader cr, String targetName, String targetDesc) {
		class Visitor extends ClassVisitor {
			int maxLocals = -1;

			Visitor() {
				super(Opcodes.ASM9);
			}

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (!(targetName.equals(name) && targetDesc.equals(descriptor))) return null;
				maxLocals = Type.getArgumentsAndReturnSizes(descriptor) >> 2;
				return new MethodVisitor(Opcodes.ASM9) {

					@Override
					public void visitMaxs(int maxStack, int maxLocals) {
						Visitor.this.maxLocals = maxLocals;
					}
				};
			}
		}
		Visitor visitor = new Visitor();
		cr.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		int maxLocals = visitor.maxLocals;
		if (maxLocals == -1) {
			throw new IllegalStateException("Unable to get max locals for " + targetName + targetDesc);
		}
		return maxLocals;
	}
}
