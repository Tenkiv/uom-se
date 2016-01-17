/*
 * Units of Measurement Implementation for Java SE
 * Copyright (c) 2005-2015, Jean-Marie Dautelle, Werner Keil, V2COM.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 *    and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of JSR-363 nor the names of its contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package tec.uom.se.format;

import static java.lang.StrictMath.E; // not in CLDC 8
import static tec.uom.se.unit.Units.*;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.text.ParsePosition;
import java.util.Formattable;
import java.util.Formatter;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.measure.Unit;
import javax.measure.UnitConverter;
import javax.measure.format.ParserException;

import com.sun.org.apache.xalan.internal.xsltc.compiler.Constants;
import tec.uom.se.AbstractConverter;
import tec.uom.se.AbstractConverter.Pair;
import tec.uom.se.AbstractUnit;
import tec.uom.se.function.AddConverter;
import tec.uom.se.function.ExpConverter;
import tec.uom.se.function.LogConverter;
import tec.uom.se.function.MultiplyConverter;
import tec.uom.se.function.RationalConverter;
import tec.uom.se.internal.format.UnitFormatParser;
import tec.uom.se.internal.format.TokenException;
import tec.uom.se.internal.format.TokenMgrError;
import tec.uom.se.unit.AnnotatedUnit;
import tec.uom.se.unit.BaseUnit;
import tec.uom.se.unit.MetricPrefix;
import tec.uom.se.unit.TransformedUnit;
import tec.uom.se.unit.Units;

/**
 * <p>
 * This class represents the local neutral format.
 * </p>
 * 
 * <h3>Here is the grammar for Units in Extended Backus-Naur Form (EBNF)</h3>
 * <p>
 * Note that the grammar has been left-factored to be suitable for use by a
 * top-down parser generator such as <a
 * href="https://javacc.dev.java.net/">JavaCC</a>
 * </p>
 * <table width="90%" align="center">
 * <tr>
 * <th colspan="3" align="left">Lexical Entities:</th>
 * </tr>
 * <tr valign="top">
 * <td>&lt;sign&gt;</td>
 * <td>:=</td>
 * <td>"+" | "-"</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;digit&gt;</td>
 * <td>:=</td>
 * <td>"0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9"</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;superscript_digit&gt;</td>
 * <td>:=</td>
 * <td>"⁰" | "¹" | "²" | "³" | "⁴" | "⁵" | "⁶" | "⁷" | "⁸" | "⁹"</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;integer&gt;</td>
 * <td>:=</td>
 * <td>(&lt;digit&gt;)+</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;number&gt;</td>
 * <td>:=</td>
 * <td>(&lt;sign&gt;)? (&lt;digit&gt;)* (".")? (&lt;digit&gt;)+ (("e" | "E")
 * (&lt;sign&gt;)? (&lt;digit&gt;)+)?</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;exponent&gt;</td>
 * <td>:=</td>
 * <td>( "^" ( &lt;sign&gt; )? &lt;integer&gt; ) <br>
 * | ( "^(" (&lt;sign&gt;)? &lt;integer&gt; ( "/" (&lt;sign&gt;)?
 * &lt;integer&gt; )? ")" ) <br>
 * | ( &lt;superscript_digit&gt; )+</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;initial_char&gt;</td>
 * <td>:=</td>
 * <td>? Any Unicode character excluding the following: ASCII control &
 * whitespace (&#92;u0000 - &#92;u0020), decimal digits '0'-'9', '('
 * (&#92;u0028), ')' (&#92;u0029), '*' (&#92;u002A), '+' (&#92;u002B), '-'
 * (&#92;u002D), '.' (&#92;u002E), '/' (&#92;u005C), ':' (&#92;u003A), '^'
 * (&#92;u005E), '²' (&#92;u00B2), '³' (&#92;u00B3), '·' (&#92;u00B7), '¹'
 * (&#92;u00B9), '⁰' (&#92;u2070), '⁴' (&#92;u2074), '⁵' (&#92;u2075), '⁶'
 * (&#92;u2076), '⁷' (&#92;u2077), '⁸' (&#92;u2078), '⁹' (&#92;u2079) ?</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;unit_identifier&gt;</td>
 * <td>:=</td>
 * <td>&lt;initial_char&gt; ( &lt;initial_char&gt; | &lt;digit&gt; )*</td>
 * </tr>
 * <tr>
 * <th colspan="3" align="left">Non-Terminals:</th>
 * </tr>
 * <tr valign="top">
 * <td>&lt;unit_expr&gt;</td>
 * <td>:=</td>
 * <td>&lt;compound_expr&gt;</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;compound_expr&gt;</td>
 * <td>:=</td>
 * <td>&lt;add_expr&gt; ( ":" &lt;add_expr&gt; )*</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;add_expr&gt;</td>
 * <td>:=</td>
 * <td>( &lt;number&gt; &lt;sign&gt; )? &lt;mul_expr&gt; ( &lt;sign&gt;
 * &lt;number&gt; )?</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;mul_expr&gt;</td>
 * <td>:=</td>
 * <td>&lt;exponent_expr&gt; ( ( ( "*" | "·" ) &lt;exponent_expr&gt; ) | ( "/"
 * &lt;exponent_expr&gt; ) )*</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;exponent_expr&gt;</td>
 * <td>:=</td>
 * <td>( &lt;atomic_expr&gt; ( &lt;exponent&gt; )? ) <br>
 * | (&lt;integer&gt; "^" &lt;atomic_expr&gt;) <br>
 * | ( ( "log" ( &lt;integer&gt; )? ) | "ln" ) "(" &lt;add_expr&gt; ")" )</td>
 * </tr>
 * <tr valign="top">
 * <td>&lt;atomic_expr&gt;</td>
 * <td>:=</td>
 * <td>&lt;number&gt; <br>
 * | &lt;unit_identifier&gt; <br>
 * | ( "(" &lt;add_expr&gt; ")" )</td>
 * </tr>
 * </table>
 * 
 * @author <a href="mailto:eric-r@northwestern.edu">Eric Russell</a>
 * @author <a href="mailto:units@catmedia.us">Werner Keil</a>
 * @version 0.6.2, $Date: 2015-10-23 $
 */
public class EBNFUnitFormat extends AbstractUnitFormat {

	// ////////////////////////////////////////////////////
	// Class variables //
	// ////////////////////////////////////////////////////

	/**
	 * 
	 */
//	private static final long serialVersionUID = 8968559300292910840L;

	/**
	 * Name of the resource bundle
	 */
	private static final String BUNDLE_NAME = "tec.uom.se.internal.format.messages"; //$NON-NLS-1$
	//= UnitFormatParser.class.getPackage()			.getName() + ".messages"; 

	/**
	 * Default locale instance. If the default locale is changed after the class
	 * is initialized, this instance will no longer be used.
	 */
	private static final EBNFUnitFormat DEFAULT_INSTANCE = new EBNFUnitFormat();
			//SymbolMap.of(toMap(L10nResources.getBundle(BUNDLE_NAME, 
				//	Locale.getDefault()), Locale.getDefault()));

	private static final String LocalFormat_Pattern = "%s";


	/**
	 * Returns the instance for the current default locale (non-ascii characters
	 * are allowed)
	 */
	public static EBNFUnitFormat getInstance() {
		return DEFAULT_INSTANCE;
	}

	/**
	 * Returns an instance for the given locale.
	 * 
	 * @param locale
	 */
//	static SimpleUnitFormat getInstance(Locale locale) {
//		return new SimpleUnitFormat(SymbolMap.of(toMap(ResourceBundle.getBundle(
//				BUNDLE_NAME, locale))), locale);
//	}

	/** Returns an instance for the given symbol map. */
	protected static EBNFUnitFormat getInstance(SymbolMap symbols, Locale locale) {
		return new EBNFUnitFormat(symbols, locale);
	}

	// //////////////////////
	// Instance variables //
	// //////////////////////
	/**
	 * The symbol map used by this instance to map between
	 * {@link org.unitsofmeasure.Unit Unit}s and <code>String</code>s, etc...
	 */
	private final transient SymbolMap symbolMap;

	// ////////////////
	// Constructors //
	// ////////////////
	/**
	 * Base constructor.
	 * 
	 */
	EBNFUnitFormat() {
		//this(SymbolMap.of(toMap(L10nResources.getBundle(
				//BUNDLE_NAME, Locale.getDefault()))), Locale.getDefault());
		this(SymbolMap.of(ResourceBundle.getBundle(BUNDLE_NAME, Locale.getDefault())), 
				Locale.getDefault());
	}
	
	/**
	 * Private constructor.
	 * 
	 * @param symbols
	 *            the symbol mapping.
	 */
	private EBNFUnitFormat(SymbolMap symbols, Locale loc) {
		symbolMap = symbols;
	}

	// //////////////////////
	// Instance methods //
	// //////////////////////
	/**
	 * Get the symbol map used by this instance to map between
	 * {@link org.unitsofmeasure.Unit Unit}s and <code>String</code>s, etc...
	 * 
	 * @return SymbolMap the current symbol map
	 */
	protected SymbolMap getSymbols() {
		return symbolMap;
	}

	// //////////////
	// Formatting //
	// //////////////
	public Appendable format(Unit<?> unit, Appendable appendable)
			throws IOException {
		formatInternal(unit, appendable);
		if (unit instanceof AnnotatedUnit<?>) {
			AnnotatedUnit<?> annotatedUnit = (AnnotatedUnit<?>) unit;
			if (annotatedUnit.getAnnotation() != null) {
				appendable.append('{');
				appendable.append(annotatedUnit.getAnnotation());
				appendable.append('}');
			}
		}
		return appendable;
	}

	public boolean isLocaleSensitive() {
		return false;
	}
	
	protected Unit<?> parse(CharSequence csq, int index)
			throws IllegalArgumentException {
		// Parsing reads the whole character sequence from the parse position.
		int start = index; //cursor != null ? cursor.getIndex() : 0;
		int end = csq.length();
		if (end <= start) {
			return Units.ONE;
		}
		String source = csq.subSequence(start, end).toString().trim();
		if (source.length() == 0) {
			return Units.ONE;
		}
		try {
			UnitFormatParser parser = new UnitFormatParser(symbolMap, new StringReader(
					source));
			Unit<?> result = parser.parseUnit();
//			if (cursor != null)
//				cursor.setIndex(end);
			return result;
		} catch (TokenException | TokenMgrError e) {
//			if (cursor != null) {
//				if (e.currentToken != null) {
//					cursor.setErrorIndex(start + e.currentToken.endColumn);
//				} else {
//					cursor.setErrorIndex(start);
//				}
//			}
			throw new ParserException(e);
		}
	}
	
	public Unit<?> parse(CharSequence csq)
			throws ParserException {
		return parse(csq, 0);
	}

	/**
	 * Format the given unit to the given StringBuffer, then return the operator
	 * precedence of the outermost operator in the unit expression that was
	 * formatted. See {@link ConverterFormat} for the constants that define the
	 * various precedence values.
	 * 
	 * @param unit
	 *            the unit to be formatted
	 * @param buffer
	 *            the <code>StringBuffer</code> to be written to
	 * @return the operator precedence of the outermost operator in the unit
	 *         expression that was output
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private int formatInternal(Unit<?> unit, Appendable buffer)
			throws IOException {
	return InternalFormater.INSTANCE.formatInternal(unit,buffer, symbolMap);
	}




	/**
	 * Formats the given <code>Formattable</code> to the given StringBuffer and returns the
	 * given precedence of the converter's mathematical operation.
	 * 
	 * @param f
	 *            the formattable to be formatted
	 * @param unitPrecedence
	 *            the operator precedence of the operation expressed by the unit
	 *            being modified by the given converter.
	 * @param buffer
	 *            the <code>StringBuffer</code> to append to.
	 * @return the given operator precedence
	 */
	private int formatFormattable(Formattable f, int unitPrecedence, StringBuilder buffer) {
		Formatter fmt = new Formatter();
		fmt.format(LocalFormat_Pattern, f);
		buffer.replace(0, 1, fmt.toString());
		fmt.close(); // XXX try Java 7 with res, but for now let's leave J6 compliant
		return unitPrecedence;
	}

	@Override
	protected Unit<?> parse(CharSequence csq, ParsePosition cursor)
			throws IllegalArgumentException {
		// TODO Auto-generated method stub
		return null;
	}
}
