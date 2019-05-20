package io.vertx.codegen.type;

import io.vertx.codegen.Helper;
import io.vertx.codegen.ModuleInfo;
import io.vertx.codegen.TypeParamInfo;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.ModuleGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.codegen.type.ApiTypeInfo.ApiTypeArgInfo;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Type info factory based on <i>java.lang.reflect</i> and {@link Type types}
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TypeReflectionFactory {

  public static TypeInfo create(Type type) {
    if (type == void.class) {
      return VoidTypeInfo.INSTANCE;
    } else if (type instanceof Class) {
      String fqcn = type.getTypeName();
      Class<?> classType = (Class<?>) type;
      if (classType.isPrimitive()) {
        return PrimitiveTypeInfo.PRIMITIVES.get(classType.getName());
      } else {
        Package pkg = classType.getPackage();
        ModuleInfo module = null;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classType.getClassLoader());
        try {
          while (pkg != null) {
            ModuleGen annotation = pkg.getAnnotation(ModuleGen.class);
            if (annotation != null) {
              module = new ModuleInfo(pkg.getName(), annotation.name(), annotation.groupPackage());
              break;
            } else {
              int pos = pkg.getName().lastIndexOf('.');
              if (pos == -1) {
                break;
              } else {
                pkg = Package.getPackage(pkg.getName().substring(0, pos));
              }
            }
          }
        } finally {
          Thread.currentThread().setContextClassLoader(loader);
        }
        if (classType.isEnum()) {
          return new EnumTypeInfo(
            fqcn,
            classType.getDeclaredAnnotation(VertxGen.class) != null,
            Stream.of(classType.getEnumConstants()).map(Object::toString).collect(Collectors.toList()),
            module,
            false
          );
        } else {
          ClassKind kind = ClassKind.getKind(fqcn, classType.getAnnotation(DataObject.class) != null, classType.getAnnotation(VertxGen.class) != null);
          List<TypeParamInfo.Class> typeParams = new ArrayList<>();
          int index = 0;
          for (java.lang.reflect.TypeVariable<? extends Class<?>> var : classType.getTypeParameters()) {
            typeParams.add(new TypeParamInfo.Class(classType.getName(), index++, var.getName()));
          }
          if (kind == ClassKind.API) {
            TypeInfo[] functionArgs = extractArgs(type, Function.class);
            ApiTypeArgInfo argInfo = new ApiTypeArgInfo(
              elementOrNull(extractArgs(type, ReadStream.class), 0),
              elementOrNull(extractArgs(type, WriteStream.class), 0),
              elementOrNull(extractArgs(type, Handler.class), 0),
              elementOrNull(extractArgs(type, Iterable.class), 0),
              elementOrNull(extractArgs(type, Iterator.class), 0),
              elementOrNull(functionArgs, 0),
              elementOrNull(functionArgs, 1)
            );
            return new ApiTypeInfo(fqcn, true, typeParams, module, false, false, argInfo);
          } else if (kind == ClassKind.DATA_OBJECT) {
            boolean _abstract = Modifier.isAbstract(classType.getModifiers());
            return new DataObjectTypeInfo(kind, fqcn, module, _abstract, false, typeParams);
          } else {
            return new ClassTypeInfo(kind, fqcn, module, false, typeParams);
          }
        }
      }
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      List<TypeInfo> args = Arrays.asList(parameterizedType.getActualTypeArguments()).
        stream().
        map(TypeReflectionFactory::create).
        collect(Collectors.toList());
      Type raw = parameterizedType.getRawType();
      return new ParameterizedTypeInfo((ClassTypeInfo) create(raw), false, args);
    } else if (type instanceof java.lang.reflect.TypeVariable) {
      java.lang.reflect.TypeVariable typeVar = (java.lang.reflect.TypeVariable) type;
      TypeParamInfo param = TypeParamInfo.create(typeVar);
      return new TypeVariableInfo(param, false, ((java.lang.reflect.TypeVariable) type).getName());
    } else {
      throw new IllegalArgumentException("Unsupported type " + type);
    }
  }

  private static <T> TypeInfo[] extractArgs(Type type, Class<T> clazz) {
    TypeVariable<Class<T>>[] classTypeVariables = clazz.getTypeParameters();
    TypeInfo[] result = new TypeInfo[classTypeVariables.length];
    for (int i = 0; i < classTypeVariables.length; i++) {
      TypeVariable<Class<T>> typeVariable = classTypeVariables[i];
      Type argType = Helper.resolveTypeParameter(type, typeVariable);
      result[i] = argType != null ? create(argType) : null;
    }
    return result;
  }

  private static TypeInfo elementOrNull(TypeInfo[] array, int index) {
    return index < array.length ? array[index] : null;
  }
}
