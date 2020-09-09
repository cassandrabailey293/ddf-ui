/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
import * as React from 'react'
import styled from 'styled-components'
const { MenuItem } = require('../../react-component/menu')
const properties = require('../../js/properties')

const formTitle = properties.i18n['form.title'] || 'Form'
const formTitleLowerCase = properties.i18n['form.title']
  ? properties.i18n['form.title'].toLowerCase()
  : 'form'

export type Props = {
  triggerQueryForm: (formId: string) => void
  triggerReset: () => void
  model: any
}

export const Divider = () => {
  return <div className="is-divider" />
}

export const Icon = styled.div`
  display: inline-block;
  text-align: center;
  width: ${({ theme }) => theme.minimumButtonSize};
  line-height: ${({ theme }) => theme.minimumButtonSize};
  height: ${({ theme }) => theme.minimumButtonSize};
`

export const Text = styled.div`
  width: 100%;
  display: inline-block;
  vertical-align: top;
  line-height: ${({ theme }) => theme.minimumButtonSize};
  height: ${({ theme }) => theme.minimumButtonSize};
`

export const SearchFormMenuItem = ({
  value,
  title,
  onClick,
  active,
  onHover,
  selected,
}: {
  value: any
  title: any
  onClick?: any
  active?: any
  onHover?: any
  selected?: any
}) => {
  return (
    <MenuItem
      value={value}
      title={`Use the ${title} ${formTitle} to construct the search.`}
      data-help={`Use the ${title} ${formTitle} to construct the search.`}
      onClick={onClick}
      active={active}
      onHover={onHover}
      selected={selected}
    >
      <Text>
        <Icon className="fa fa-search" />
        {title}
      </Text>
    </MenuItem>
  )
}

export const ResetMenuItem = ({
  value,
  onClick,
  active,
  onHover,
}: {
  value: any
  onClick: any
  active?: any
  onHover?: any
}) => {
  return (
    <MenuItem
      value={value}
      title={`Resets the search ${formTitleLowerCase}.`}
      data-help={`Resets the search ${formTitleLowerCase}.`}
      onClick={onClick}
      active={active}
      onHover={onHover}
    >
      <Text>
        <Icon className="fa fa-undo" />
        Reset
      </Text>
    </MenuItem>
  )
}
