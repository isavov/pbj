package com.hedera.hashgraph.pbj.compiler.impl.generators;

import com.hedera.hashgraph.pbj.compiler.impl.*;
import com.hedera.hashgraph.pbj.compiler.impl.grammar.Protobuf3Parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.hedera.hashgraph.pbj.compiler.impl.Common.*;
import static com.hedera.hashgraph.pbj.compiler.impl.generators.EnumGenerator.EnumValue;
import static com.hedera.hashgraph.pbj.compiler.impl.generators.EnumGenerator.createEnum;

/**
 * Code generator that parses protobuf files and generates nice Java source for record files for each message type and
 * enum.
 */
@SuppressWarnings({"StringConcatenationInLoop", "EscapedSpace"})
public final class ModelGenerator implements Generator {

	/**
	 * {@inheritDoc}
	 */
	public void generate(final Protobuf3Parser.MessageDefContext msgDef,
						 final File destinationSrcDir,
						 File destinationTestSrcDir, final ContextualLookupHelper lookupHelper) throws IOException {
		final var javaRecordName = lookupHelper.getUnqualifiedClassForMessage(FileType.MODEL, msgDef);
		final String modelPackage = lookupHelper.getPackageForMessage(FileType.MODEL, msgDef);
		final File javaFile = getJavaFile(destinationSrcDir, modelPackage, javaRecordName);
		String javaDocComment = (msgDef.docComment()== null) ? "" :
				cleanDocStr(msgDef.docComment().getText().replaceAll("\n \\*\s*\n","\n * <p>\n"));
		String deprecated = "";
		final List<Field> fields = new ArrayList<>();
		final List<String> oneofEnums = new ArrayList<>();
		final List<String> oneofGetters = new ArrayList<>();
		final Set<String> imports = new TreeSet<>();
		imports.add("com.hedera.hashgraph.pbj.runtime.io");
		for(var item: msgDef.messageBody().messageElement()) {
			if (item.messageDef() != null) { // process sub messages
				generate(item.messageDef(), destinationSrcDir, destinationTestSrcDir, lookupHelper);
			} else if (item.oneof() != null) { // process one ofs
				final OneOfField oneOfField = new OneOfField(item.oneof(),javaRecordName, lookupHelper);
				final String enumName = oneOfField.nameCamelFirstUpper()+"OneOfType";
				final int maxIndex = oneOfField.fields().get(oneOfField.fields().size()-1).fieldNumber();
				final Map<Integer,EnumValue> enumValues = new HashMap<>();
				for(final Field field: oneOfField.fields()) {
					final String fieldType = field.protobufFieldType();
					final String javaFieldType = javaPrimitiveToObjectType(field.javaFieldType());
					enumValues.put(field.fieldNumber(), new EnumValue(field.name(),field.deprecated(),
							"/** " + cleanDocStr(field.comment()) + "\n     */"));
					// generate getters for one ofs
					oneofGetters.add("""
							/**
							 * Direct typed getter for one of field %s.
							 *
							 * @return optional for one of value. Optional.empty() if one of is not this one
							 */
							public Optional<%s> %s() {
								return %s.kind() == %s.%s ? Optional.of((%s)%s.value()) : Optional.empty();
							}
							""".formatted(
							field.nameCamelFirstLower(),
							javaFieldType,
							field.nameCamelFirstLower(),
							oneOfField.nameCamelFirstLower(),
							enumName,
							camelToUpperSnake(field.name()),
							javaFieldType,
							oneOfField.nameCamelFirstLower()
					).replaceAll("\n","\n"+FIELD_INDENT));
					if ("Bytes".equals(fieldType)) imports.add("com.hedera.hashgraph.pbj.runtime.io");
					if (field.type() == Field.FieldType.MESSAGE){
						field.addAllNeededImports(imports, true, false, false, false);
					}
				}
				final String enumComment = """
									/**
									 * Enum for the type of "%s" oneof value
									 */""".formatted(oneOfField.name());
				final String enumString = createEnum(FIELD_INDENT,enumComment ,"",enumName,maxIndex,enumValues, true);
				oneofEnums.add(enumString);
				fields.add(oneOfField);
				imports.add("com.hedera.hashgraph.pbj.runtime");
			} else if (item.mapField() != null) { // process map fields
				System.err.println("Encountered a mapField that was not handled in "+javaRecordName);
			} else if (item.field() != null && item.field().fieldName() != null) {
				final SingleField field = new SingleField(item.field(), lookupHelper);
				fields.add(field);
				field.addAllNeededImports(imports, true, false, false, false);
			} else if (item.optionStatement() != null){
				if ("deprecated".equals(item.optionStatement().optionName().getText())) {
					deprecated = "@Deprecated ";
				} else {
					System.err.println("Unhandled Option: "+item.optionStatement().getText());
				}
			} else if (item.reserved() == null){ // ignore reserved and warn about anything else
				System.err.println("ModelGenerator Warning - Unknown element: "+item+" -- "+item.getText());
			}
		}
		// process field java doc and insert into record java doc
		if (!fields.isEmpty()) {
			String recordJavaDoc = javaDocComment.length() > 0 ?
					javaDocComment.replaceAll("\n\s*\\*/","") :
					"/**\n * "+javaRecordName;
			recordJavaDoc += "\n *";
			for(var field: fields) {
				recordJavaDoc += "\n * @param "+field.nameCamelFirstLower()+" "+
							field.comment()
								.replaceAll("\n", "\n *         "+" ".repeat(field.nameCamelFirstLower().length()));
			}
			recordJavaDoc += "\n */";
			javaDocComment = cleanDocStr(recordJavaDoc);
		}
		// === Build Body Content
		String bodyContent = "";
		// constructor
		if (fields.stream().anyMatch(f -> f instanceof OneOfField || f.optionalValueType())) {
			bodyContent += """
     
					/**
					 * Override the default constructor adding input validation
					 * %s
					 */
					public %s {
					%s
					}
					
					""".formatted(
					fields.stream().map(field -> "\n * @param "+field.nameCamelFirstLower()+" "+
						field.comment()
						.replaceAll("\n", "\n *         "+" ".repeat(field.nameCamelFirstLower().length()))
					).collect(Collectors.joining()),
					javaRecordName,
					fields.stream()
							.filter(f -> f instanceof OneOfField || f.optionalValueType())
							.map(ModelGenerator::generateConstructorCode)
							.collect(Collectors.joining("\n"))
					).replaceAll("\n","\n"+FIELD_INDENT);
		}
		// oneof getters
		bodyContent += String.join("\n    ", oneofGetters);
		bodyContent += "\n"+FIELD_INDENT;
		// builder copy method
		bodyContent += """
				/**
				 * Return a builder for building a copy of this model object. It will be pre-populated with all the data from this
				 * model object.
				 *
				 * @return a pre-populated builder
				 */
				Builder copyBuilder() {
					return new Builder(%s);
				}
				
				"""
				.formatted(fields.stream().map(Field::nameCamelFirstLower).collect(Collectors.joining(", ")))
				.replaceAll("\n","\n"+FIELD_INDENT);
		// generate builder
		bodyContent += generateBuilder(msgDef, fields, lookupHelper);
		bodyContent += "\n"+FIELD_INDENT;
		// oneof enums
		bodyContent += String.join("\n    ", oneofEnums);
		// === Build file
		try (FileWriter javaWriter = new FileWriter(javaFile)) {
			//noinspection SpellCheckingInspection
			javaWriter.write("""
					package %s;
					%s
					import java.util.Optional;
					
					%s
					%spublic record %s(
					    %s
					){
						%s
					}
					""".formatted(
					modelPackage,
					imports.isEmpty() ? "" : imports.stream().collect(Collectors.joining(".*;\nimport ","\nimport ",".*;\n")),
					javaDocComment,
					deprecated,
					javaRecordName,
					fields.stream().map(field ->
						FIELD_INDENT+field.javaFieldType() + " " + field.nameCamelFirstLower()
					).collect(Collectors.joining(",\n    ")),
					bodyContent
			));
		}
	}

	private static String generateBuilder(final Protobuf3Parser.MessageDefContext msgDef, List<Field> fields, final ContextualLookupHelper lookupHelper) {
		final String javaRecordName = msgDef.messageName().getText();
		List<String> builderMethods = new ArrayList<>();
		for (Field field: fields) {
			if (field.type() == Field.FieldType.ONE_OF) {
				final OneOfField oneOfField = (OneOfField) field;
				for (Field subField: oneOfField.fields()) {
					builderMethods.add("""
						/**
						 * $fieldDoc
						 *
						 * @param $fieldName value to set
						 * @return builder to continue building with
						 */
						public Builder $fieldName($fieldType $fieldName) {
							this.$oneOfFieldName = new OneOf<>($oneOfEnumValue, $fieldName);
							return this;
						}"""
						.replace("$fieldDoc",subField.comment()
								.replaceAll("\n", "\n * "))
						.replace("$fieldName",subField.nameCamelFirstLower())
						.replace("$fieldType",subField.javaFieldType())
						.replace("$oneOfFieldName",field.nameCamelFirstLower())
						.replace("$oneOfEnumValue",oneOfField.getEnumClassRef()+"."+camelToUpperSnake(subField.name()))
						.replaceAll("\n","\n"+FIELD_INDENT));
				}
			} else {
				builderMethods.add("""
						/**
						 * $fieldDoc
						 *
						 * @param $fieldName value to set
						 * @return builder to continue building with
						 */
						public Builder $fieldName($fieldType $fieldName) {
							this.$fieldName = $fieldName;
							return this;
						}"""
						.replace("$fieldDoc",field.comment()
								.replaceAll("\n", "\n * "))
						.replace("$fieldName",field.nameCamelFirstLower())
						.replace("$fieldType",field.javaFieldType())
						.replaceAll("\n","\n"+FIELD_INDENT));
			}
		}
		return """
			/**
			 * Builder class for easy creation, ideal for clean code where performance is not critical. In critical performance
			 * paths use the constructor directly.
			 */
			public static final class Builder {
				$fields;
		
				/**
				 * Create an empty builder
				 */
				public Builder() {}
		
				/**
				 * Create a pre-populated builder
				 * $constructorParamDocs
				 */
				public Builder($constructorParams) {
					$constructorCode;
				}
		
				/**
				 * Build a new model record with data set on builder
				 *
				 * @return new model record with data set
				 */
				public $javaRecordName build() {
					return new $javaRecordName($recordParams);
				}
		
				$builderMethods
			}"""
				.replace("$fields", fields.stream().map(field ->
						"private " + field.javaFieldType() + " " + field.nameCamelFirstLower() +
								" = " + getDefaultValue(field, msgDef, lookupHelper)
						).collect(Collectors.joining(";\n    ")))
				.replace("$constructorParamDocs",fields.stream().map(field ->
						"\n     * @param "+field.nameCamelFirstLower()+" "+
								field.comment().replaceAll("\n", "\n     *         "+" ".repeat(field.nameCamelFirstLower().length()))
						).collect(Collectors.joining(", ")))
				.replace("$constructorParams",fields.stream().map(field ->
						field.javaFieldType() + " " + field.nameCamelFirstLower()
						).collect(Collectors.joining(", ")))
				.replace("$constructorCode",fields.stream().map(field ->
						"this." + field.nameCamelFirstLower() + " = " + field.nameCamelFirstLower()
						).collect(Collectors.joining(";\n"+FIELD_INDENT+FIELD_INDENT)))
				.replace("$javaRecordName",javaRecordName)
				.replace("$recordParams",fields.stream().map(Field::nameCamelFirstLower).collect(Collectors.joining(", ")))
				.replace("$builderMethods",builderMethods.stream().collect(Collectors.joining("\n\n"+FIELD_INDENT)))
				.replaceAll("\n","\n"+FIELD_INDENT);
	}

	private static String getDefaultValue(Field field, final Protobuf3Parser.MessageDefContext msgDef, final ContextualLookupHelper lookupHelper) {
		if (field.type() == Field.FieldType.ONE_OF) {
			return lookupHelper.getFullyQualifiedMessageClassname(FileType.PARSER, msgDef)+"."+field.javaDefault();
		} else {
			return field.javaDefault();
		}
	}

	private static String generateConstructorCode(final Field f) {
		StringBuilder sb = new StringBuilder(FIELD_INDENT+"""
									if ($fieldName == null) {
										throw new NullPointerException("Parameter '$fieldName' must be supplied and can not be null");
									}""".replace("$fieldName", f.nameCamelFirstLower()));
		if (f instanceof final OneOfField oof) {
			for (Field subField: oof.fields()) {
				if(subField.optionalValueType()) {
					sb.append("""
       
							// handle special case where protobuf does not have destination between a OneOf with optional
							// value of empty vs a unset OneOf.
							if($fieldName.kind() == $fieldUpperNameOneOfType.$subFieldNameUpper && ((Optional)$fieldName.value()).isEmpty()) {
								$fieldName = new OneOf<>($fieldUpperNameOneOfType.UNSET, null);
							}"""
							.replace("$fieldName", f.nameCamelFirstLower())
							.replace("$fieldUpperName", f.nameCamelFirstUpper())
							.replace("$subFieldNameUpper", camelToUpperSnake(subField.name()))
					);
				}
			}
		}
		return sb.toString().replaceAll("\n","\n"+FIELD_INDENT);
	}
}