package com.voxeo.tropo.transport.reversetcp;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldRequirementType;
import org.apache.thrift.TProcessor;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolUtil;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;

public class BindListener {

  public interface Iface {

    public String bind(String secrete) throws TException;

  }

  public static class Client implements Iface {
    public Client(TProtocol prot) {
      this(prot, prot);
    }

    public Client(TProtocol iprot, TProtocol oprot) {
      iprot_ = iprot;
      oprot_ = oprot;
    }

    protected TProtocol iprot_;
    protected TProtocol oprot_;

    protected int seqid_;

    public TProtocol getInputProtocol() {
      return this.iprot_;
    }

    public TProtocol getOutputProtocol() {
      return this.oprot_;
    }

    public String bind(String secrete) throws TException {
      send_bind(secrete);
      return recv_bind();
    }

    public void send_bind(String secrete) throws TException {
      oprot_.writeMessageBegin(new TMessage("bind", TMessageType.CALL, seqid_));
      bind_args args = new bind_args();
      args.secrete = secrete;
      args.write(oprot_);
      oprot_.writeMessageEnd();
      oprot_.getTransport().flush();
    }

    public String recv_bind() throws TException {
      TMessage msg = iprot_.readMessageBegin();
      if (msg.type == TMessageType.EXCEPTION) {
        TApplicationException x = TApplicationException.read(iprot_);
        iprot_.readMessageEnd();
        throw x;
      }
      bind_result result = new bind_result();
      result.read(iprot_);
      iprot_.readMessageEnd();
      if (result.isSetSuccess()) {
        return result.success;
      }
      throw new TApplicationException(TApplicationException.MISSING_RESULT, "bind failed: unknown result");
    }

  }

  public static class Processor implements TProcessor {
    public Processor(Iface iface) {
      iface_ = iface;
      processMap_.put("bind", new bind());
    }

    protected static interface ProcessFunction {
      public void process(int seqid, TProtocol iprot, TProtocol oprot) throws TException;
    }

    private Iface iface_;
    protected final HashMap<String, ProcessFunction> processMap_ = new HashMap<String, ProcessFunction>();

    public boolean process(TProtocol iprot, TProtocol oprot) throws TException {
      TMessage msg = iprot.readMessageBegin();
      ProcessFunction fn = processMap_.get(msg.name);
      if (fn == null) {
        TProtocolUtil.skip(iprot, TType.STRUCT);
        iprot.readMessageEnd();
        TApplicationException x = new TApplicationException(TApplicationException.UNKNOWN_METHOD, "Invalid method name: '" + msg.name + "'");
        oprot.writeMessageBegin(new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid));
        x.write(oprot);
        oprot.writeMessageEnd();
        oprot.getTransport().flush();
        return true;
      }
      fn.process(msg.seqid, iprot, oprot);
      return true;
    }

    private class bind implements ProcessFunction {
      public void process(int seqid, TProtocol iprot, TProtocol oprot) throws TException {
        bind_args args = new bind_args();
        args.read(iprot);
        iprot.readMessageEnd();
        bind_result result = new bind_result();
        // result.success = iface_.bind(args.secrete);
        result.success = ((TSocketFactory) iface_).bind(args.secrete, iprot.getTransport());
        oprot.writeMessageBegin(new TMessage("bind", TMessageType.REPLY, seqid));
        result.write(oprot);
        oprot.writeMessageEnd();
        oprot.getTransport().flush();
      }

    }

  }

  public static class bind_args implements TBase, java.io.Serializable, Cloneable {
    private static final TStruct STRUCT_DESC = new TStruct("bind_args");
    private static final TField SECRETE_FIELD_DESC = new TField("secrete", TType.STRING, (short) 1);

    private String secrete;
    public static final int SECRETE = 1;

    private final Isset __isset = new Isset();

    private static final class Isset implements java.io.Serializable {
    }

    public static final Map<Integer, FieldMetaData> metaDataMap = Collections.unmodifiableMap(new HashMap<Integer, FieldMetaData>() {
      {
        put(SECRETE, new FieldMetaData("secrete", TFieldRequirementType.DEFAULT, new FieldValueMetaData(TType.STRING)));
      }
    });

    static {
      FieldMetaData.addStructMetaDataMap(bind_args.class, metaDataMap);
    }

    public bind_args() {
    }

    public bind_args(String secrete) {
      this();
      this.secrete = secrete;
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public bind_args(bind_args other) {
      if (other.isSetSecrete()) {
        this.secrete = other.secrete;
      }
    }

    @Override
    public bind_args clone() {
      return new bind_args(this);
    }

    public String getSecrete() {
      return this.secrete;
    }

    public void setSecrete(String secrete) {
      this.secrete = secrete;
    }

    public void unsetSecrete() {
      this.secrete = null;
    }

    // Returns true if field secrete is set (has been asigned a value) and false
    // otherwise
    public boolean isSetSecrete() {
      return this.secrete != null;
    }

    public void setFieldValue(int fieldID, Object value) {
      switch (fieldID) {
      case SECRETE:
        if (value == null) {
          unsetSecrete();
        }
        else {
          setSecrete((String) value);
        }
        break;

      default:
        throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
      }
    }

    public Object getFieldValue(int fieldID) {
      switch (fieldID) {
      case SECRETE:
        return getSecrete();

      default:
        throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
      }
    }

    // Returns true if field corresponding to fieldID is set (has been asigned a
    // value) and false otherwise
    public boolean isSet(int fieldID) {
      switch (fieldID) {
      case SECRETE:
        return isSetSecrete();
      default:
        throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
      }
    }

    @Override
    public boolean equals(Object that) {
      if (that == null)
        return false;
      if (that instanceof bind_args)
        return this.equals((bind_args) that);
      return false;
    }

    public boolean equals(bind_args that) {
      if (that == null)
        return false;

      boolean this_present_secrete = true && this.isSetSecrete();
      boolean that_present_secrete = true && that.isSetSecrete();
      if (this_present_secrete || that_present_secrete) {
        if (!(this_present_secrete && that_present_secrete))
          return false;
        if (!this.secrete.equals(that.secrete))
          return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      HashCodeBuilder builder = new HashCodeBuilder();

      boolean present_secrete = true && (isSetSecrete());
      builder.append(present_secrete);
      if (present_secrete)
        builder.append(secrete);

      return builder.toHashCode();
    }

    public void read(TProtocol iprot) throws TException {
      TField field;
      iprot.readStructBegin();
      while (true) {
        field = iprot.readFieldBegin();
        if (field.type == TType.STOP) {
          break;
        }
        switch (field.id) {
        case SECRETE:
          if (field.type == TType.STRING) {
            this.secrete = iprot.readString();
          }
          else {
            TProtocolUtil.skip(iprot, field.type);
          }
          break;
        default:
          TProtocolUtil.skip(iprot, field.type);
          break;
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      validate();
    }

    public void write(TProtocol oprot) throws TException {
      validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (this.secrete != null) {
        oprot.writeFieldBegin(SECRETE_FIELD_DESC);
        oprot.writeString(this.secrete);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("bind_args(");
      boolean first = true;

      sb.append("secrete:");
      if (this.secrete == null) {
        sb.append("null");
      }
      else {
        sb.append(this.secrete);
      }
      first = false;
      sb.append(")");
      return sb.toString();
    }

    public void validate() throws TException {
      // check for required fields
      // check that fields of type enum have valid values
    }

  }

  public static class bind_result implements TBase, java.io.Serializable, Cloneable {
    private static final TStruct STRUCT_DESC = new TStruct("bind_result");
    private static final TField SUCCESS_FIELD_DESC = new TField("success", TType.STRING, (short) 0);

    private String success;
    public static final int SUCCESS = 0;

    private final Isset __isset = new Isset();

    private static final class Isset implements java.io.Serializable {
    }

    public static final Map<Integer, FieldMetaData> metaDataMap = Collections.unmodifiableMap(new HashMap<Integer, FieldMetaData>() {
      {
        put(SUCCESS, new FieldMetaData("success", TFieldRequirementType.DEFAULT, new FieldValueMetaData(TType.STRING)));
      }
    });

    static {
      FieldMetaData.addStructMetaDataMap(bind_result.class, metaDataMap);
    }

    public bind_result() {
    }

    public bind_result(String success) {
      this();
      this.success = success;
    }

    /**
     * Performs a deep copy on <i>other</i>.
     */
    public bind_result(bind_result other) {
      if (other.isSetSuccess()) {
        this.success = other.success;
      }
    }

    @Override
    public bind_result clone() {
      return new bind_result(this);
    }

    public String getSuccess() {
      return this.success;
    }

    public void setSuccess(String success) {
      this.success = success;
    }

    public void unsetSuccess() {
      this.success = null;
    }

    // Returns true if field success is set (has been asigned a value) and false
    // otherwise
    public boolean isSetSuccess() {
      return this.success != null;
    }

    public void setFieldValue(int fieldID, Object value) {
      switch (fieldID) {
      case SUCCESS:
        if (value == null) {
          unsetSuccess();
        }
        else {
          setSuccess((String) value);
        }
        break;

      default:
        throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
      }
    }

    public Object getFieldValue(int fieldID) {
      switch (fieldID) {
      case SUCCESS:
        return getSuccess();

      default:
        throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
      }
    }

    // Returns true if field corresponding to fieldID is set (has been asigned a
    // value) and false otherwise
    public boolean isSet(int fieldID) {
      switch (fieldID) {
      case SUCCESS:
        return isSetSuccess();
      default:
        throw new IllegalArgumentException("Field " + fieldID + " doesn't exist!");
      }
    }

    @Override
    public boolean equals(Object that) {
      if (that == null)
        return false;
      if (that instanceof bind_result)
        return this.equals((bind_result) that);
      return false;
    }

    public boolean equals(bind_result that) {
      if (that == null)
        return false;

      boolean this_present_success = true && this.isSetSuccess();
      boolean that_present_success = true && that.isSetSuccess();
      if (this_present_success || that_present_success) {
        if (!(this_present_success && that_present_success))
          return false;
        if (!this.success.equals(that.success))
          return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      HashCodeBuilder builder = new HashCodeBuilder();

      boolean present_success = true && (isSetSuccess());
      builder.append(present_success);
      if (present_success)
        builder.append(success);

      return builder.toHashCode();
    }

    public void read(TProtocol iprot) throws TException {
      TField field;
      iprot.readStructBegin();
      while (true) {
        field = iprot.readFieldBegin();
        if (field.type == TType.STOP) {
          break;
        }
        switch (field.id) {
        case SUCCESS:
          if (field.type == TType.STRING) {
            this.success = iprot.readString();
          }
          else {
            TProtocolUtil.skip(iprot, field.type);
          }
          break;
        default:
          TProtocolUtil.skip(iprot, field.type);
          break;
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      validate();
    }

    public void write(TProtocol oprot) throws TException {
      oprot.writeStructBegin(STRUCT_DESC);

      if (this.isSetSuccess()) {
        oprot.writeFieldBegin(SUCCESS_FIELD_DESC);
        oprot.writeString(this.success);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder("bind_result(");
      boolean first = true;

      sb.append("success:");
      if (this.success == null) {
        sb.append("null");
      }
      else {
        sb.append(this.success);
      }
      first = false;
      sb.append(")");
      return sb.toString();
    }

    public void validate() throws TException {
      // check for required fields
      // check that fields of type enum have valid values
    }

  }

}
