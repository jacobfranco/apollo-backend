package com.apollo.backend.navs;

import com.apollo.backend.*;

import com.rpl.rama.*;
import org.apache.thrift.*;

import static com.apollo.backend.ApolloHelpers.*;

public class TField implements Navigator<TBase> {
  String _fieldName;

  public TField(String fieldName) {
    _fieldName = fieldName;
  }

  @Override
  public Object select(TBase obj, Next next) {
    return next.invokeNext(getTFieldByName(obj, _fieldName));
  }

  @Override
  public TBase transform(TBase obj, Next next) {
    Object curr = getTFieldByName(obj, _fieldName);
    setTFieldByName(obj, _fieldName, next.invokeNext(curr));
    return obj;
  }

  
}