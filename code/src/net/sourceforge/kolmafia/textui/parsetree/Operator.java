/**
 * Copyright (c) 2005-2017, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION ) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE ) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia.textui.parsetree;

import java.io.PrintStream;

import net.sourceforge.kolmafia.VYKEACompanionData;

import net.sourceforge.kolmafia.textui.DataTypes;
import net.sourceforge.kolmafia.textui.Interpreter;
import net.sourceforge.kolmafia.textui.Parser;

public class Operator
	extends ParseTreeNode
{
	String operator;

	// For runtime error messages
	String fileName;
	int lineNumber;

	public Operator( final String operator, final Parser parser )
	{
		this.operator = operator;
		this.fileName = parser.getShortFileName();
		this.lineNumber = parser.getLineNumber();
	}

	public boolean equals( final String op )
	{
		return this.operator.equals( op );
	}

	public boolean precedes( final Operator oper )
	{
		return this.operStrength() > oper.operStrength();
	}

	private int operStrength()
	{
		if ( this.operator == Parser.POST_INCREMENT ||
		     this.operator == Parser.POST_DECREMENT )
		{
			return 14;
		}

		if ( this.operator.equals( "!" ) ||
		     this.operator.equals( "~" ) ||
		     this.operator.equals( "contains" ) ||
		     this.operator.equals( "remove" ) ||
		     this.operator == Parser.PRE_INCREMENT ||
		     this.operator == Parser.PRE_DECREMENT )
		{
			return 13;
		}

		if ( this.operator.equals( "**" ) )
		{
			return 12;
		}

		if ( this.operator.equals( "*" ) ||
		     this.operator.equals( "/" ) ||
		     this.operator.equals( "%" ) )
		{
			return 11;
		}

		if ( this.operator.equals( "+" ) ||
		     this.operator.equals( "-" ) )
		{
			return 10;
		}

		if ( this.operator.equals( "<<" ) ||
		     this.operator.equals( ">>" ) ||
		     this.operator.equals( ">>>" ) )
		{
			return 9;
		}

		if ( this.operator.equals( "<" ) ||
		     this.operator.equals( ">" ) ||
		     this.operator.equals( "<=" ) ||
		     this.operator.equals( ">=" ) )
		{
			return 8;
		}

		if ( this.operator.equals( "==" ) ||
		     this.operator.equals( Parser.APPROX ) ||
		     this.operator.equals( "!=" ) )
		{
			return 7;
		}

		if ( this.operator.equals( "&" ) )
		{
			return 6;
		}

		if ( this.operator.equals( "^" ) )
		{
			return 5;
		}

		if ( this.operator.equals( "|" ) )
		{
			return 4;
		}

		if ( this.operator.equals( "&&" ) )
		{
			return 3;
		}

		if ( this.operator.equals( "||" ) )
		{
			return 2;
		}

		if ( this.operator.equals( "?" ) ||
		     this.operator.equals( ":" ) )
		{
			return 1;
		}

		return -1;
	}

	public static boolean isStringLike( Type type )
	{
		return  type.equals( DataTypes.TYPE_STRING ) ||
			type.equals( DataTypes.TYPE_BUFFER ) ||
			type.equals( DataTypes.TYPE_LOCATION ) ||
			type.equals( DataTypes.TYPE_STAT ) ||
			type.equals( DataTypes.TYPE_MONSTER ) ||
			type.equals( DataTypes.TYPE_ELEMENT ) ||
			type.equals( DataTypes.TYPE_COINMASTER ) ||
			type.equals( DataTypes.TYPE_PHYLUM ) ||
			type.equals( DataTypes.TYPE_BOUNTY );
	}

	public boolean isArithmetic()
	{
		return  this.operator.equals( "+" ) ||
			this.operator.equals( "-" ) ||
			this.operator.equals( "*" ) ||
			this.operator.equals( "/" ) ||
			this.operator.equals( "%" ) ||
			this.operator.equals( "**" ) ||
			this.operator == Parser.PRE_INCREMENT ||
			this.operator == Parser.PRE_DECREMENT ||
			this.operator == Parser.POST_INCREMENT ||
			this.operator == Parser.POST_DECREMENT;
	}

	public boolean isBoolean()
	{
		return  this.operator.equals( "&&" ) ||
			this.operator.equals( "||" );
	}

	public boolean isLogical()
	{
		return  this.operator.equals( "&" ) ||
			this.operator.equals( "|" ) ||
			this.operator.equals( "^" ) ||
			this.operator.equals( "~" ) ||
			this.operator.equals( "&=" ) ||
			this.operator.equals( "^=" ) ||
			this.operator.equals( "|=" );
	}

	public boolean isInteger()
	{
		return  this.operator.equals( "<<" ) ||
			this.operator.equals( ">>" ) ||
			this.operator.equals( ">>>" ) ||
			this.operator.equals( "<<=" ) ||
			this.operator.equals( ">>=" ) ||
			this.operator.equals( ">>>=" );
	}

	public boolean isComparison()
	{
		return  this.operator.equals( "==" ) ||
			this.operator.equals( Parser.APPROX ) ||
			this.operator.equals( "!=" ) ||
			this.operator.equals( "<" ) ||
			this.operator.equals( ">" ) ||
			this.operator.equals( "<=" ) ||
			this.operator.equals( ">=" );
	}

	@Override
	public String toString()
	{
		return this.operator;
	}

	private Value compareValues( final Interpreter interpreter, Value leftValue, Value rightValue )
	{
		Type ltype = leftValue.getType();
		Type rtype = rightValue.getType();
		boolean bool;

		// If either side is non-numeric, perform string comparison
		if ( Operator.isStringLike( ltype ) || Operator.isStringLike( rtype ) )
		{
			String lstring = leftValue.toString();
			String rstring = rightValue.toString();
			int c = this.operator.equals( Parser.APPROX ) ?
				lstring.compareToIgnoreCase( rstring ) :
				lstring.compareTo( rstring );
			bool = ( this.operator.equals( "==" ) || this.operator.equals( Parser.APPROX ) ) ? c == 0 :
				this.operator.equals( "!=" ) ? c != 0 :
				this.operator.equals( ">=" ) ? c >= 0 :
				this.operator.equals( "<=" ) ? c <= 0 :
				this.operator.equals( ">" ) ? c > 0 :
				this.operator.equals( "<" ) ? c < 0 :
				false;
		}

		// If either value is a float, coerce to float and compare.

		else if ( ltype.equals( DataTypes.TYPE_FLOAT ) || rtype.equals( DataTypes.TYPE_FLOAT ) )
		{
			double lfloat = leftValue.toFloatValue().floatValue();
			double rfloat = rightValue.toFloatValue().floatValue();
			bool = ( this.operator.equals( "==" ) || this.operator.equals( Parser.APPROX ) ) ? lfloat == rfloat :
			       this.operator.equals( "!=" ) ? lfloat != rfloat :
			       this.operator.equals( ">=" ) ? lfloat >= rfloat :
			       this.operator.equals( "<=" ) ? lfloat <= rfloat :
			       this.operator.equals( ">" ) ? lfloat > rfloat :
			       this.operator.equals( "<" ) ? lfloat < rfloat :
			       false;
		}

		// VYKEA companions have a "name" component which should not be compared
		else if ( ltype.equals( DataTypes.TYPE_VYKEA ) || rtype.equals( DataTypes.TYPE_VYKEA ) )
		{
			VYKEACompanionData v1 = (VYKEACompanionData)( leftValue.content );
			VYKEACompanionData v2 = (VYKEACompanionData)( rightValue.content );
			int c = v1.compareTo( v2 );
			bool = ( this.operator.equals( "==" ) || this.operator.equals( Parser.APPROX ) ) ? c == 0 :
				this.operator.equals( "!=" ) ? c != 0 :
				this.operator.equals( ">=" ) ? c >= 0 :
				this.operator.equals( "<=" ) ? c <= 0 :
				this.operator.equals( ">" ) ? c > 0 :
				this.operator.equals( "<" ) ? c < 0 :
				false;
		}

		// Otherwise, compare integers
		else
		{
			long lint = leftValue.intValue();
			long rint = rightValue.intValue();
			bool = ( this.operator.equals( "==" ) || this.operator.equals( Parser.APPROX ) ) ? lint == rint :
			       this.operator.equals( "!=" ) ? lint != rint :
			       this.operator.equals( ">=" ) ? lint >= rint :
			       this.operator.equals( "<=" ) ? lint <= rint :
			       this.operator.equals( ">" ) ? lint > rint :
			       this.operator.equals( "<" ) ? lint < rint :
			       false;
		}

		Value result = bool ? DataTypes.TRUE_VALUE : DataTypes.FALSE_VALUE;
		if ( interpreter.isTracing() )
		{
			interpreter.trace( "<- " + result );
		}
		interpreter.traceUnindent();
		return result;
	}

	private Value performArithmetic( final Interpreter interpreter, Value leftValue, Value rightValue )
	{
		Type ltype = leftValue.getType();
		Type rtype = rightValue.getType();
		Value result;

		// If either side is non-numeric, perform string operations
		if ( Operator.isStringLike( ltype) || Operator.isStringLike( rtype ) )
		{
			// Since we only do string concatenation, we should
			// only get here if the operator is "+".
			if ( !this.operator.equals( "+" ) )
			{
				throw Interpreter.runtimeException( "Operator '" + this.operator + "' applied to string operands", this.fileName, this.lineNumber );
			}

			String string = leftValue.toStringValue().toString() + rightValue.toStringValue().toString();
			result = new Value( string );
		}

		// If either value is a float, coerce to float

		else if ( ltype.equals( DataTypes.TYPE_FLOAT ) || rtype.equals( DataTypes.TYPE_FLOAT ) )
		{
			double rfloat = rightValue.toFloatValue().floatValue();
			if (  ( this.operator.equals( "/" ) || this.operator.equals( "%" ) ) &&
			      rfloat == 0.0 )
			{
				throw Interpreter.runtimeException( "Division by zero", this.fileName, this.lineNumber );
			}

			double lfloat = leftValue.toFloatValue().floatValue();

			double val = 0.0;

			if ( this.operator.equals( "**" ) )
			{
				val = Math.pow( lfloat, rfloat );
				if ( Double.isNaN( val ) || Double.isInfinite( val ) )
				{
					throw Interpreter.runtimeException( "Invalid exponentiation: cannot take " + lfloat + " ** " + rfloat, this.fileName, this.lineNumber );
				}
			}
			else
			{
				val =   this.operator.equals( "+" ) ? lfloat + rfloat :
					this.operator.equals( "-" ) ? lfloat - rfloat :
					this.operator.equals( "*" ) ? lfloat * rfloat :
					this.operator.equals( "/" ) ? lfloat / rfloat :
					this.operator.equals( "%" ) ? lfloat % rfloat :
					0.0;
			}

			result = DataTypes.makeFloatValue( val );
		}

		// If this is a logical operator, return an int or boolean
		else if ( this.isLogical() )
		{
			long lint = leftValue.intValue();
			long rint = rightValue.intValue();
			long val =
				this.operator.equals( "&" ) ? lint & rint :
				this.operator.equals( "^" ) ? lint ^ rint :
				this.operator.equals( "|" ) ? lint | rint :
				0;
			result = ltype.equals( DataTypes.TYPE_BOOLEAN ) ?
				DataTypes.makeBooleanValue( val != 0 ) :
				DataTypes.makeIntValue( val );
		}

		// Otherwise, perform arithmetic on integers

		else
		{
			long rint = rightValue.intValue();
			if (  ( this.operator.equals( "/" ) || this.operator.equals( "%" ) ) &&
			      rint == 0 )
			{
				throw Interpreter.runtimeException( "Division by zero", this.fileName, this.lineNumber );
			}

			long lint = leftValue.intValue();
			long val =
				this.operator.equals( "+" ) ? lint + rint :
				this.operator.equals( "-" ) ? lint - rint :
				this.operator.equals( "*" ) ? lint * rint :
				this.operator.equals( "/" ) ? lint / rint :
				this.operator.equals( "%" ) ? lint % rint :
				this.operator.equals( "**" ) ? (long) Math.pow( lint, rint ) :
				this.operator.equals( "<<" ) ? lint << rint :
				this.operator.equals( ">>" ) ? lint >> rint :
				this.operator.equals( ">>>" ) ? lint >>> rint :
				0;
			result = DataTypes.makeIntValue( val );
		}

		if ( interpreter.isTracing() )
		{
			interpreter.trace( "<- " + result );
		}
		interpreter.traceUnindent();
		return result;
	}

	public Value applyTo( final Interpreter interpreter, final Value lhs )
	{
		interpreter.traceIndent();
		if ( interpreter.isTracing() )
		{
			interpreter.trace( "Operator: " + this.operator );
		}

		// Unary operator with special evaluation of argument
		if ( this.operator.equals( "remove" ) )
		{
			CompositeReference operand = (CompositeReference) lhs;
                        if ( interpreter.isTracing() )
                        {
                                interpreter.traceIndent();
                                interpreter.trace( "Operand: " + operand );
                                interpreter.traceUnindent();
                        }
			Value result = operand.removeKey( interpreter );
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "<- " + result );
			}
			interpreter.traceUnindent();
			return result;
		}

		interpreter.traceIndent();
		if ( interpreter.isTracing() )
		{
			interpreter.trace( "Operand: " + lhs );
		}

		Value leftValue = lhs.execute( interpreter );
		interpreter.captureValue( leftValue );
		if ( leftValue == null )
		{
			leftValue = DataTypes.VOID_VALUE;
		}

		if ( interpreter.isTracing() )
		{
			interpreter.trace( "[" + interpreter.getState() + "] <- " + leftValue.toQuotedString() );
		}
		interpreter.traceUnindent();

		if ( interpreter.getState() == Interpreter.STATE_EXIT )
		{
			interpreter.traceUnindent();
			return null;
		}

		Value result;

		// Unary Operators
		if ( this.operator.equals( "!" ) )
		{
			result = DataTypes.makeBooleanValue( leftValue.intValue() == 0 );
		}
		else if ( this.operator.equals( "~" ) )
		{
			long val = leftValue.intValue();
			result =
				leftValue.getType().equals( DataTypes.TYPE_BOOLEAN ) ?
				DataTypes.makeBooleanValue( val == 0 ) :
				DataTypes.makeIntValue( ~val );
		}
		else if ( this.operator.equals( "-" ) )
		{
			if ( lhs.getType().equals( DataTypes.TYPE_INT ) )
			{
				result = DataTypes.makeIntValue( 0 - leftValue.intValue() );
			}
			else if ( lhs.getType().equals( DataTypes.TYPE_FLOAT ) )
			{
				result = DataTypes.makeFloatValue( 0.0 - leftValue.floatValue() );
			}
			else
			{
				throw Interpreter.runtimeException( "Internal error: Unary minus can only be applied to numbers", this.fileName, this.lineNumber );
			}
		}
		else if ( this.operator == Parser.PRE_INCREMENT || this.operator == Parser.POST_INCREMENT )
		{
			if ( lhs.getType().equals( DataTypes.TYPE_INT ) )
			{
				result = DataTypes.makeIntValue( leftValue.intValue() + 1 );
			}
			else if ( lhs.getType().equals( DataTypes.TYPE_FLOAT ) )
			{
				result = DataTypes.makeFloatValue( leftValue.floatValue() + 1.0 );
			}
			else
			{
				throw Interpreter.runtimeException( "Internal error: pre/post increment can only be applied to numbers", this.fileName, this.lineNumber );
			}
		}
		else if ( this.operator == Parser.PRE_DECREMENT || this.operator == Parser.POST_DECREMENT )
		{
			if ( lhs.getType().equals( DataTypes.TYPE_INT ) )
			{
				result = DataTypes.makeIntValue( leftValue.intValue() - 1 );
			}
			else if ( lhs.getType().equals( DataTypes.TYPE_FLOAT ) )
			{
				result = DataTypes.makeFloatValue( leftValue.floatValue() - 1.0 );
			}
			else
			{
				throw Interpreter.runtimeException( "Internal error: pre/post increment can only be applied to numbers", this.fileName, this.lineNumber );
			}
		}
		else
		{
			throw Interpreter.runtimeException( "Internal error: unknown unary operator \"" + this.operator + "\"", this.fileName, this.lineNumber );
		}

		if ( interpreter.isTracing() )
		{
			interpreter.trace( "<- " + result );
		}

		interpreter.traceUnindent();
		return result;
	}

	public Value applyTo( final Interpreter interpreter, final Value lhs, final Value rhs )
	{
		interpreter.traceIndent();
		if ( interpreter.isTracing() )
		{
			interpreter.trace( "Operator: " + this.operator );
		}

		interpreter.traceIndent();
		if ( interpreter.isTracing() )
		{
			interpreter.trace( "Operand 1: " + lhs );
		}

		Value leftValue = lhs.execute( interpreter );
		interpreter.captureValue( leftValue );
		if ( leftValue == null )
		{
			leftValue = DataTypes.VOID_VALUE;
		}
		if ( interpreter.isTracing() )
		{
			interpreter.trace( "[" + interpreter.getState() + "] <- " + leftValue.toQuotedString() );
		}
		interpreter.traceUnindent();

		if ( interpreter.getState() == Interpreter.STATE_EXIT )
		{
			interpreter.traceUnindent();
			return null;
		}

		// Unknown operator
		if ( rhs == null )
		{
			throw Interpreter.runtimeException( "Internal error: missing right operand.", this.fileName, this.lineNumber );
		}

		// Binary operators with optional right values
		if ( this.operator.equals( "||" ) )
		{
			if ( leftValue.intValue() == 1 )
			{
				if ( interpreter.isTracing() )
				{
					interpreter.trace( "<- " + DataTypes.TRUE_VALUE );
				}
				interpreter.traceUnindent();
				return DataTypes.TRUE_VALUE;
			}
			interpreter.traceIndent();
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "Operand 2: " + rhs );
			}
			Value rightValue = rhs.execute( interpreter );
			interpreter.captureValue( rightValue );
			if ( rightValue == null )
			{
				rightValue = DataTypes.VOID_VALUE;
			}
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "[" + interpreter.getState() + "] <- " + rightValue.toQuotedString() );
			}
			interpreter.traceUnindent();
			if ( interpreter.getState() == Interpreter.STATE_EXIT )
			{
				interpreter.traceUnindent();
				return null;
			}
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "<- " + rightValue );
			}
			interpreter.traceUnindent();
			return rightValue;
		}

		if ( this.operator.equals( "&&" ) )
		{
			if ( leftValue.intValue() == 0 )
			{
				interpreter.traceUnindent();
				if ( interpreter.isTracing() )
				{
					interpreter.trace( "<- " + DataTypes.FALSE_VALUE );
				}
				return DataTypes.FALSE_VALUE;
			}
			interpreter.traceIndent();
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "Operand 2: " + rhs );
			}
			Value rightValue = rhs.execute( interpreter );
			interpreter.captureValue( rightValue );
			if ( rightValue == null )
			{
				rightValue = DataTypes.VOID_VALUE;
			}
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "[" + interpreter.getState() + "] <- " + rightValue.toQuotedString() );
			}
			interpreter.traceUnindent();
			if ( interpreter.getState() == Interpreter.STATE_EXIT )
			{
				interpreter.traceUnindent();
				return null;
			}
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "<- " + rightValue );
			}
			interpreter.traceUnindent();
			return rightValue;
		}

		// Ensure type compatibility of operands
		if ( !Parser.validCoercion( lhs.getType(), rhs.getType(), this ) )
		{
			throw Interpreter.runtimeException( "Internal error: left hand side and right hand side do not correspond", this.fileName, this.lineNumber );
		}

		// Special binary operator: <aggref> contains <any>
		if ( this.operator.equals( "contains" ) )
		{
			interpreter.traceIndent();
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "Operand 2: " + rhs );
			}
			Value rightValue = rhs.execute( interpreter );
			interpreter.captureValue( rightValue );
			if ( rightValue == null )
			{
				rightValue = DataTypes.VOID_VALUE;
			}
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "[" + interpreter.getState() + "] <- " + rightValue.toQuotedString() );
			}
			interpreter.traceUnindent();
			if ( interpreter.getState() == Interpreter.STATE_EXIT )
			{
				interpreter.traceUnindent();
				return null;
			}
			Value result = DataTypes.makeBooleanValue( leftValue.contains( rightValue ) );
			if ( interpreter.isTracing() )
			{
				interpreter.trace( "<- " + result );
			}
			interpreter.traceUnindent();
			return result;
		}

		// Binary operators
		interpreter.traceIndent();
		if ( interpreter.isTracing() )
		{
			interpreter.trace( "Operand 2: " + rhs );
		}
		Value rightValue = rhs.execute( interpreter );
		interpreter.captureValue( rightValue );
		if ( rightValue == null )
		{
			rightValue = DataTypes.VOID_VALUE;
		}
		if ( interpreter.isTracing() )
		{
			interpreter.trace( "[" + interpreter.getState() + "] <- " + rightValue.toQuotedString() );
		}
		interpreter.traceUnindent();
		if ( interpreter.getState() == Interpreter.STATE_EXIT )
		{
			interpreter.traceUnindent();
			return null;
		}

		// Comparison operators
		if ( this.isComparison() )
		{
			return this.compareValues( interpreter, leftValue, rightValue );
		}

		// Arithmetic operators
		if ( this.isArithmetic() || this.isLogical() || this.isInteger() )
		{
			return this.performArithmetic( interpreter, leftValue, rightValue );
		}

		// Unknown operator
		throw Interpreter.runtimeException( "Internal error: unknown binary operator \"" + this.operator + "\"", this.fileName, this.lineNumber );
	}

	@Override
	public Value execute( final Interpreter interpreter )
	{
		return null;
	}

	@Override
	public void print( final PrintStream stream, final int indent )
	{
		Interpreter.indentLine( stream, indent );
		stream.println( "<OPER " + this.operator + ">" );
	}
}
